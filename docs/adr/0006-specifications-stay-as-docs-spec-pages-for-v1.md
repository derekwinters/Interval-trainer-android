# 6. Specifications stay as `docs/spec` pages for v1

- **Status:** accepted
- **Date:** 2026-09-12
- **Decided by:** @derekwinters
- **Issue:** [#41](https://github.com/derekwinters/Interval-trainer-android/issues/41)
- **Research:** [`docs/research/openspec-and-okf.md`](../research/openspec-and-okf.md) (issue
  [#39](https://github.com/derekwinters/Interval-trainer-android/issues/39))
- **Specification:** the style this preserves is [`docs/spec/build.md`](../spec/build.md) and
  [`docs/spec/signing.md`](../spec/signing.md); the v1 specification is
  [#33](https://github.com/derekwinters/Interval-trainer-android/issues/33)
- **Deferred to:** [#48](https://github.com/derekwinters/Interval-trainer-android/issues/48),
  milestone `v0.2`

## Context

The owner wants to use OpenSpec. That is not in question here and this decision does not overturn
it. What was in question is *when* — and the moment the question arrived was the worst possible one,
because the first real specification of the app's behaviour was about to be written. Everything in
`docs/spec/` today specifies the scaffolding: how the build is wired, how a release is signed. The
cue page on [#30](https://github.com/derekwinters/Interval-trainer-android/issues/30) and the v1
specification on [#33](https://github.com/derekwinters/Interval-trainer-android/issues/33) are the
first pages that say what the app *does*.

The primary-source research on
[#39](https://github.com/derekwinters/Interval-trainer-android/issues/39), recorded in
[`docs/research/openspec-and-okf.md`](../research/openspec-and-okf.md), establishes what adopting
OpenSpec would mean:

- **It is `Fission-AI/OpenSpec`** — MIT, distributed on npm, requiring Node.js ≥ 20.19.0, at 1.13.0
  when the research was read on 2026-09-11. It has two halves that can be adopted separately: a
  corpus of durable specifications under `openspec/specs/`, and a change workflow of proposal,
  delta specs, tasks and archive.
- **OpenSpec identifies a requirement by its heading text.** There is no identifier. The delta
  vocabulary includes `## RENAMED Requirements` as a first-class operation precisely because the
  text *is* the identity — rename the heading and you have declared a change. This repository does
  the opposite: a requirement is a stable label, `BUILD-030` or `SIGN-042`, and that label is cited
  from tests, from the traceability tables at the foot of each specification page, from ADRs, and
  from the issue history. A handle that survives a reworded sentence is the whole point of it.
- **`openspec validate` checks structure, not truth.** It checks that a change has a delta, that
  required sections are present, that scenarios use the right header. **Nothing in it checks that a
  requirement has a test**, and nothing in it expresses the `auto`/`manual` marking that
  [`build.md`](../spec/build.md) uses to be honest about which of its seventeen requirements a
  machine actually verifies.
- **The change half overlaps with ground this repository already occupies.** Proposal, design and
  task list is what `wayfinder` does for planning and what ai-sdlc does for the pipeline — issues,
  triage, approval, the pull-request gates. Adopting it means either running two planning
  vocabularies or retiring one, and that is a decision with its own cost, not a free addition.

None of that is disqualifying. It is, taken together, a migration with a concentrated cost in one
place — identifiers — and no urgency behind it.

## Decision

**For v1, specifications stay as `docs/spec/*.md` pages with stable requirement labels, explicit
invariants, per-requirement `auto`/`manual` marking, and a traceability table.** No OpenSpec, no
OKF, no `openspec/` directory, no new runtime, no new dependency, no continuous-integration change.
The cue page closing [#30](https://github.com/derekwinters/Interval-trainer-android/issues/30) and
the v1 specification on [#33](https://github.com/derekwinters/Interval-trainer-android/issues/33)
are written in the style of `build.md` and `signing.md`.

**Adoption is deferred, not refused.** It is tracked by
[#48](https://github.com/derekwinters/Interval-trainer-android/issues/48) at milestone `v0.2`,
which carries OpenSpec and Google's Open Knowledge Format together so documentation is settled once
with both tools in view rather than in two passes. The document types are unchanged for v1:
`docs/spec/`, `docs/adr/`, `docs/research/`, `CONTEXT.md`.

**The decisive reason is the timing, and nothing else.** Changing the format a specification is
written in, at the moment the first one is written, makes it impossible to attribute a problem to
the format or to the content. If the cue specification turns out to be vague, or the v1
specification turns out to be unreviewable, there would be no way to tell whether the tool was
wrong or whether the thinking was — and the obvious response, blaming whichever one was newer,
would be a guess. After v0.1 there is a real specification to convert and real experience of what
was wanted from it. Conversion is then a mechanical job against a known artefact rather than a bet.

## A correction: the argument this decision does not rest on

The case against adopting OpenSpec now was originally argued on **ai-sdlc's spec-to-test
traceability gate** — that OpenSpec's heading-text identity would break a gate this repository
depends on, and that fixing it means an issue filed upstream against `derekwinters/ai-sdlc`, since
gates are not this repository's to change.

**That gate is not installed in this repository.** `.ai-sdlc/repo-config.yml` declares the
`consistency` capability and `.ai-sdlc/adoption.md` lists it as resolved, but no workflow caller for
it exists under `.github/workflows/`. A pull request here runs `pr-title-lint`, `closing-keyword`
and the Gradle build in `pr.yml`, and nothing else. The house rules say specification-before-code is
*"Enforced: the spec↔test traceability gate"*; here it is enforced by people.

It was found by checking a claim because the claim was load-bearing, and it is recorded here for
two reasons. The first is that **the decision does not depend on it**: the timing argument above
stands on its own and would stand unchanged if the gate were running tomorrow. The second is that an
ADR that quietly drops a disproven argument teaches the next reader nothing — worse, it invites them
to re-derive the same wrong reason and act on it. The gap is a defect in its own right, independent
of OpenSpec, because a declared capability with no caller is a claim of enforcement the repository
does not have. It is tracked as
[#49](https://github.com/derekwinters/Interval-trainer-android/issues/49).

## Alternatives considered

**Adopt the specification half now** — move `docs/spec/` into `openspec/specs/` and write the cue
and v1 pages there. Rejected on requirement identity, which is not a detail but the opposite of how
this repository works. A traceability table keyed on *"Requirement: Duration formatting renders
m:ss"* breaks the moment someone improves the wording, and every `BUILD-` and `SIGN-` citation in
the tests, the ADRs and the issue history stops resolving. The `auto`/`manual` distinction has no
OpenSpec equivalent at all, and it is doing real work: thirteen of `build.md`'s seventeen
requirements are configuration facts no unit test can reach, and saying so is what stops the
traceability table being a fiction. There is a second edge to this: the consistency gate is meant to
be turned on (#49), and turning it on over a corpus with no identifiers is harder than turning it on
over one that has them — so adopting now would make the cheaper of the two orderings unavailable.

**Adopt the change half only** — `proposal.md`, `design.md`, `tasks.md` per change, with
`skip_specs: true` so no second requirements corpus exists. Rejected because it has to earn its
place against `wayfinder`, which already plans, and ai-sdlc, which already runs the issue lifecycle
and the pull-request gates. It would be occupying ground that is taken, and the research notes that
it is also using the tool against its grain: with `skip_specs` on every change, `openspec archive`
merges nothing, which is the main thing OpenSpec is for.

**Adopt both halves now.** Rejected as the two rejections above, at once, plus the cost of learning
a new workflow during the only window in this project where the specification content is itself
unproven.

**Drop OpenSpec entirely.** Rejected. The owner asked for it specifically, the research found
nothing disqualifying about the tool — only badly timed — and a strong argument is a reason to put a
decision in front of someone rather than to make it for them. The same reasoning keeps OKF alive on
#48 rather than closing it out, with the case against it recorded there: OKF's worked examples are
BigQuery tables and metrics, its v0.2 headline feature is attested computation this app will never
use, its benefits scale at hundreds of documents where this repository has roughly a dozen, and the
thing it most plausibly would be wanted for — knowing what kind of document you are reading — is an
explicit OKF non-goal, for which the research names Diátaxis instead.

## Consequences

- **The next two specification pages are written in the existing style, and that is now a
  commitment rather than an accident.** The cue page and the v1 specification get requirement
  labels, invariants, `auto`/`manual` marking and a traceability table with a count, because that
  is what `build.md` and `signing.md` do. Anyone reaching for a different shape should read this
  ADR first.
- **Every existing citation keeps resolving.** `BUILD-030`–`033` are named by
  `app/src/test/java/com/derekwinters/intervaltrainer/FormatSecondsTest.kt` through the traceability
  table, `SIGN-060` and `SIGN-061` are cited from `pr.yml`, and ADR 0005 cites `BUILD-002` and
  `BUILD-030`–`033` by label when it says where code has to move. None of that moves in v1.
- **#48 inherits a real specification to convert, not an empty directory.** That is the point of
  the deferral: whoever takes it up will be able to say what OpenSpec costs, because there will be a
  concrete page to hold against it, and will know from experience what the `auto`/`manual` marking
  was worth.
- **The repository claims an enforcement it does not have until #49 lands.** The house rules name
  the spec↔test gate; this repository does not run it. Until that is fixed, specification-before-code
  and the traceability tables are conventions held up by review, and a pull request that quietly
  drops a requirement from a table will not be stopped by anything automatic.
- **The milestone naming discrepancy is real and should be fixed on the map rather than worked
  around.** The wayfinder map on [#22](https://github.com/derekwinters/Interval-trainer-android/issues/22)
  says feature issues go to a `0.1.0` milestone; this repository's milestones are `v0.1` through
  `v0.20`. The map is what is wrong. Creating a `0.1.0` milestone to match it would leave two
  milestones meaning the same release, which `milestone_ordering: semver` would then sort into a
  sequence nobody intended.
