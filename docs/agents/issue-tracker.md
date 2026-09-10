# Issue tracker: GitHub

Issues — and the wayfinder map with its tickets — live as GitHub issues in this repository,
`derekwinters/Interval-trainer-android`. There is no `gh` CLI here. Every read and every write goes
through the vocabulary of the `github-api` skill (`.claude/skills/github-api/SKILL.md`), on
whichever surface is available: the GitHub MCP tools, or the REST API through `curl`, which the
session's proxy authenticates. That skill says what may be done, what may not, and the identity,
pagination and redaction rules; this page says how the conventions the planning skills expect map
onto it.

ai-sdlc owns triage, the pipeline labels, milestones and the pull-request gates. Nothing here
overrides that. Read the `ai-sdlc` skill before touching any of it.

## Conventions

The operation names in bold are the `github-api` vocabulary. Every one of them takes an issue
**number** except where a database **id** is called out.

- **Create an issue** — MCP `issue_write` with `method: create`, or
  `POST /repos/{owner}/{repo}/issues`. A multi-line body is a JSON string; build it with a heredoc
  into a file and send the file, never by hand-escaping. Not in the vocabulary by name; see
  *Outside the vocabulary* below.
- **Read an issue** — **issue**: MCP `issue_read` with `method: get`, or
  `GET /repos/{owner}/{repo}/issues/{n}`. The response's `id` is the database id; `number` is what
  appears in a title or URL. Follow with **comments** (`method: get_comments`, or
  `.../issues/{n}/comments`) and **labels**.
- **List issues** — **issues**: MCP `list_issues`, or
  `GET /repos/{owner}/{repo}/issues?state=open&labels=...`. Both paginate. Never report a count
  from a page you did not read to the end. The REST endpoint also returns pull requests — drop any
  item carrying a `pull_request` key.
- **Comment on an issue** — **comment**: MCP `add_issue_comment`, or
  `POST .../issues/{n}/comments`.
- **Apply or remove labels** — **set_labels**: MCP `issue_write` with `method: update` and the
  full `labels` list, or `PUT .../issues/{n}/labels`. Read the current labels first; the write
  replaces the set. Never put a pipeline label on a wayfinder ticket.
- **Close** — **forbidden**. An issue closes because a pull request carrying `Closes #n` merged,
  and nothing else. See *Resolve* under *Wayfinding operations*.

Infer the repository from `git remote -v`.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature
requests; `/triage` reads this flag. The `triage` skill is not installed here — ai-sdlc owns
triage — so the flag is recorded for completeness and left off.)_

Were it `yes`, pull requests would run through the same labels and states as issues, read through
MCP `pull_request_read` and `list_pull_requests` (or `GET /repos/{owner}/{repo}/pulls`), keeping
only an `author_association` of `CONTRIBUTOR`, `FIRST_TIME_CONTRIBUTOR` or `NONE`.

GitHub shares one number space across issues and pull requests, so a bare `#42` may be either.
Resolve it with a pull-request read and fall back to an issue read.

## When a skill says "publish to the issue tracker"

Create a GitHub issue.

## When a skill says "fetch the relevant ticket"

Read the issue and its comments (**issue**, then **comments**).

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets. Wayfinder
tickets carry **no pipeline label** and are **not admitted to ai-sdlc triage**: the map is the
plan, and its tickets never enter `ai-triage-queued` or any later state.

- **Map**: a single issue labelled `wayfinder:map`, holding the Destination / Notes /
  Decisions-so-far / Not-yet-specified / Out-of-scope body. Map tickets carry no milestone.
- **Child ticket**: an issue attached to the map as a native GitHub **sub-issue**. Create it with
  MCP `issue_write` and `parent_issue_number`, or attach an existing one with `sub_issue_write`
  (`method: add`) or `POST .../issues/{map}/sub_issues` — both take the child's database **id**,
  not its number. Put `Part of #<map>` at the top of the child body as well, so the link reads in
  plain text. Labels: exactly one `wayfinder:<type>` — `research`, `prototype`, `grilling` or
  `task`. Once claimed, the ticket is assigned to the driving dev (`@derekwinters`).
- **Blocking**: GitHub's **native issue dependencies** — the canonical, UI-visible representation.
  **add_blocked_by**: `POST /repos/{owner}/{repo}/issues/{child}/dependencies/blocked_by` with
  `{"issue_id": <blocker-db-id>}`, where `<blocker-db-id>` is the blocker's numeric **database
  id** — read it first with **issue_id** (the `id` field of an issue read), and never pass the
  `#number` or the `node_id`. Both are integers, so the wrong one succeeds silently against some
  other issue or against nothing. **remove_blocked_by**:
  `DELETE .../issues/{child}/dependencies/blocked_by/{blocker-db-id}`. **blocked_by**:
  `GET .../issues/{child}/dependencies/blocked_by`, or the `issue_dependencies_summary.blocked_by`
  count on an issue read (open blockers only — the live gate). There is no MCP tool for
  dependencies; the `issue-blockers` skill has the self-block and cycle checks to run before
  adding an edge. A `Blocked by: #n` line in prose is drift, not a blocker: convert it, never
  honour it. A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open children (MCP `issue_read` with `method: get_sub_issues`,
  or `GET .../issues/{map}/sub_issues`, filtered to `state: open`), drop any with an open blocker
  (`issue_dependencies_summary.blocked_by > 0`) or an assignee; first in map order wins.
- **Claim**: assign the ticket to the driving dev — MCP `issue_write` with `method: update` and
  `assignees`, or `POST .../issues/{n}/assignees` — as the session's **first** write, before any
  work. The assignee is the claim: an open, unassigned ticket is unclaimed.
- **Resolve** — this repository's closing rule, from `CLAUDE.md`:
  1. Post the answer as a **resolution comment** on the ticket (**comment**). For a task ticket
     the comment says what was done and records any resulting facts later tickets depend on.
  2. Hand the resulting document — spec, ADR, glossary, research note — to the `dev` agent, which
     opens the pull request that lands it, carrying `Closes #n`. One ticket, one branch, one pull
     request. The ticket closes when that pull request merges. **Nobody closes a ticket by hand**,
     and no session calls a close operation.
  3. Once the ticket is closed, append a context pointer — gist, then the ticket's name wrapping
     its link — to the map's *Decisions so far* (**set_body** on the map, touching only that
     section; it is the generated index, and the rest of the body is what a person wrote).
- **Out of scope**: a ticket found to sit past the destination produces no document, and the
  closing rule names nothing else that may close it. Do not close it by hand. Record the ruling
  under *Out of scope* on the map, leave the ticket open, and ask on the map how such a ticket
  should close — the rule is silent, and a silent rule is a question, not a licence.

## Outside the vocabulary

The `github-api` vocabulary was written for ai-sdlc's pipeline and names no operation for
creating an issue, attaching a sub-issue, or assigning. Wayfinder cannot chart or claim without
them, and none of the three is on the vocabulary's forbidden list — none is destructive or
irreversible — so this page treats them as permitted on the same surfaces, under the same
identity, pagination and redaction rules. Everything the vocabulary forbids stays forbidden here:
no closing, reopening or deleting an issue, no deleting a comment, no merging a pull request.
