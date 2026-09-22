---
type: Architecture Decision Record
title: "ADR 0008: OKF v0.2 frontmatter on the documents that are not specifications"
description: "This repository adopts Google's Open Knowledge Format v0.2 as frontmatter on its ADRs, research notes and glossary, and deliberately leaves docs/spec out."
status: stable
generated: { by: claude-code, at: 2026-09-22T00:00:00Z }
---

# 8. OKF v0.2 frontmatter on the documents that are not specifications

- **Status:** accepted
- **Date:** 2026-09-22
- **Decided by:** @derekwinters
- **Issue:** [#48](https://github.com/derekwinters/Interval-trainer-android/issues/48)
- **Research:** [`docs/research/openspec-and-okf.md`](../research/openspec-and-okf.md) (issue
  [#39](https://github.com/derekwinters/Interval-trainer-android/issues/39))
- **Format targeted:** **OKF v0.2** —
  [`GoogleCloudPlatform/open-knowledge-format`](https://github.com/GoogleCloudPlatform/open-knowledge-format),
  Apache-2.0
- **Constrains:** [#137](https://github.com/derekwinters/Interval-trainer-android/issues/137), the
  OpenSpec adoption epic, by the boundary in [Decision, point 5](#5-okf-frontmatter-never-goes-inside-openspec)
- **Does not disturb:** [ADR 0006](0006-specifications-stay-as-docs-spec-pages-for-v1.md), which
  keeps `docs/spec/` as it is. Nothing in that tree changes here.

## Context

[#48](https://github.com/derekwinters/Interval-trainer-android/issues/48) originally carried two
adoptions at once — OpenSpec and OKF — because both answer "where does documentation live, and in
what form". The two were split on 2026-09-22: OpenSpec moved to
[#137](https://github.com/derekwinters/Interval-trainer-android/issues/137) on `v0.4`, because it is
structural and expensive (334 requirements and roughly a thousand identifier citations outside
`docs/spec/`), while OKF is additive by construction — frontmatter on files that already exist,
optionally an `index.md`, nothing restructured and nothing migrated. Holding the cheap change behind
the expensive one bought nothing. This ADR records the OKF half only.

**What OKF is.** A format, not a tool:
[a specification](https://github.com/GoogleCloudPlatform/open-knowledge-format/blob/main/SPEC.md)
for markdown files with YAML frontmatter, published by Google Cloud, at v0.2. There is nothing to
install, no runtime, no registry and no required tooling. Exactly one frontmatter key is required —
`type` — and conformance is deliberately permissive: a consumer must not reject a document for
missing optional fields, unknown `type` values, unknown extra keys, broken cross-links or a missing
`index.md`. On top of the required key it offers provenance (`sources`), trust (`generated`,
`verified`) and lifecycle (`status`, `stale_after`) families, all optional.

**The case against adopting it is strong, and was put before the decision was made.** The research
note's *"strongest argument against"* section makes it plainly: OKF was written for data-catalog
knowledge — its worked examples are BigQuery tables, datasets, metrics and an income statement; its
headline v0.2 feature is *attested computation*, machinery for proving a financial number was
computed the sanctioned way, which an Android interval trainer will never use; and its real
benefits — progressive disclosure, trust tiers, graph traversal — are benefits at the scale of
hundreds of agent-generated documents, against this repository's thirteen. It also does not answer
the question someone adopting "a documentation approach" usually wants answered: *what kind of
document is this and what belongs in it* is an explicit OKF **non-goal**, and Diátaxis is what
answers it.

**The owner's answer, recorded on the issue:** adopt it anyway, deliberately and not by default —
the motive is to learn the format by using it. That is a decision about what this repository is for
as much as what it contains, and the argument above is understood and overridden on those grounds,
not overlooked. The research note's own response to its own objection points the same way: the
honest conclusion is to adopt *less* of OKF rather than none, because the frontmatter is nearly free
and makes the corpus self-describing before it grows, while the index and attestation machinery can
stay on the shelf indefinitely.

## Decision

**This repository targets OKF v0.2 for the documents under `docs/` that are not specifications, and
for `CONTEXT.md`.** Six points, in the order they matter.

### 1. Which documents carry frontmatter, and which do not

**In:** `docs/adr/*.md`, `docs/research/*.md`, and `CONTEXT.md` — thirteen documents when this
decision lands, fourteen with this ADR.

**Out, deliberately:**

- **`docs/spec/*.md`.** These are specifications, and [ADR
  0006](0006-specifications-stay-as-docs-spec-pages-for-v1.md) already settled their shape for v1:
  stable requirement labels, explicit invariants, `auto`/`manual` marking and a traceability table.
  What becomes of them is [#137](https://github.com/derekwinters/Interval-trainer-android/issues/137)'s
  question, not this one. OKF's own fit, per the research, is documents that are *not*
  specifications; adding frontmatter to a page whose format is about to be decided elsewhere would
  be work done twice.
- **`docs/agents/issue-tracker.md`**, `README.md`, `CHANGELOG.md`, `CLAUDE.md` and everything under
  `.ai-sdlc/`. These are instructions to agents and tools, or files another system generates and
  rewrites; none is knowledge about the app.

### 2. Which keys, and what they mean here

| Key | Rule |
| --- | --- |
| `type` | Required by OKF. One of `Architecture Decision Record`, `Research Note`, `Glossary`. |
| `title` | Human-readable display name. ADRs use `ADR 000n: <heading>` so the number travels with the title. |
| `description` | One sentence. It is what an index or a search snippet would show. |
| `status` | Written explicitly on every document. See point 4. |
| `generated` | `{ by, at }` on every document, because every one of them was agent-authored. |
| `sources` | On the research notes only, mirroring the source families each note already declares in prose. |

OKF's `type` taxonomy is uncontrolled — the specification says values are not registered centrally
and consumers must tolerate unknown ones — so the three values above are this repository's own
vocabulary, and a new document picks from them rather than inventing a fourth without a reason.

`generated.by` is `claude-code` on all fourteen documents: every commit that wrote one carries a
`Co-Authored-By: Claude` trailer. OKF's actor convention (§7) asks for `<producer>/<version>`, and
**no model version is recorded anywhere in this repository's history**, so the version is omitted
rather than guessed. `generated.at` is the timestamp of the commit that last changed the document's
content — not the commit that adds its frontmatter, which changes no content.

`verified` is not used. It would claim a human confirmed a document against its sources, and nothing
in this repository records that having happened. An absent `verified` reads as *unverified*, which
is the truth.

`stale_after` is not used either. Every research note opens with a "Date checked" and a warning that
versions drift, and `stale_after` is that sentence made machine-readable — but it takes an **absolute
instant**, and choosing one means deciding how long a research note stays fresh. Nothing has decided
that, so nothing is written.

### 3. No `index.md`, and where the version pin lives instead

**No root `index.md` is added.** The research note's recommendation is to skip it until there is
enough to index, and thirteen documents is not enough; OKF itself requires no index and forbids a
consumer from rejecting a bundle for missing one.

That has one consequence worth naming: a bundle-root `index.md` is *the only place OKF permits*
`okf_version: "0.2"` to be written. With no index file, the version pin lives in prose — in this
ADR's header and in `CLAUDE.md` — the same way `.ai-sdlc/adoption.md` pins ai-sdlc's version rather
than leaving it to be inferred. **OKF v0.2 is the version this repository targets**, and v0.2 has
already made breaking changes to v0.1 (`timestamp` superseded by `generated.at`, and a body
`# Citations` list superseded by frontmatter `sources`), so which version is meant is not a detail.

### 4. `status` is always written, because absent means `stable`

OKF reads an **absent `status` as `stable`**. A consumer pointed at this tree would therefore read
an unfinished document as settled fact. So every document carries `status` explicitly, and the value
is chosen rather than defaulted.

Today **nothing is `draft`**: all seven existing ADRs are accepted, all five research notes are
complete as written, and `CONTEXT.md` is the language in force. All thirteen are `status: stable`.

The rule going forward, so the next document does not default silently:

- `draft` — a document that lands before what it describes is settled: an ADR opened for discussion,
  a research note still being gathered.
- `stable` — accepted, complete, in force.
- `deprecated` — kept for its links and its history, no longer current. An ADR superseded by a later
  one becomes `deprecated` here.

OKF's `status` is about the *document's* lifecycle and is a different axis from an ADR's own
**Status:** line, which is about the *decision*. They agree today and can diverge: an accepted
decision that a later ADR overturns stays `accepted` in its own header — that is the history — while
its OKF `status` becomes `deprecated`.

### 5. OKF frontmatter never goes inside `openspec/`

There is no `openspec/` directory today and will not be one until
[#137](https://github.com/derekwinters/Interval-trainer-android/issues/137) lands, and whichever
shape #137 chooses, this boundary holds: **no OKF frontmatter inside it.** `openspec archive`
machine-rewrites files in that tree. Frontmatter there would survive — nothing in OpenSpec strips it
— but nothing in OpenSpec would maintain it either, so `status` and `generated.at` would go stale
silently, which is precisely the failure OKF v0.2 exists to prevent. Recorded now because it costs a
paragraph now and an argument later.

### 6. Nothing reads these files

No tool in this repository parses frontmatter, no continuous-integration check validates it, and
none is added here. This is a convention held by the people and agents writing the documents. If it
proves worth enforcing, that is an ai-sdlc gate and therefore an issue filed upstream, not a script
added to this repository.

## Alternatives considered

**Do not adopt OKF at all.** The case for this is the research note's own strongest argument,
summarised in [Context](#context) above, and it is a good one. It loses to a reason outside the
format's merits: the owner wants to learn OKF by using it, and a repository of thirteen documents is
a cheap place to learn it. The cost of being wrong is deleting some lines from the top of fourteen
files.

**Adopt Diátaxis instead.** Diátaxis answers *what kind of document is this and what belongs in it*,
which is the question OKF explicitly declines. But it is not an alternative to OKF, because it is not
a file format — OKF would happily carry a Diátaxis-shaped corpus, since `type:` is producer-defined
and could as easily be `Tutorial` or `Reference`. Nothing here forecloses Diátaxis later; it is a
separate question and does not need answering to write a frontmatter block.

**Adopt the whole of OKF: `index.md` files, `log.md` files, trust tiers, attested computation.**
Rejected as ceremony at this scale. Thirteen documents have no navigation problem, attested
computation has no use in an interval trainer, and a `log.md` per directory duplicates what `git
log` already answers. Each of these can be added later without changing anything written today,
because OKF is additive.

**Claim `docs/` as a conformant OKF bundle.** Not claimed, and not possible under point 1: OKF
conformance is a property of a whole tree, and `docs/spec/` and `docs/agents/` deliberately carry no
frontmatter. `docs/adr/` and `docs/research/` each satisfy OKF v0.2's conformance clause once this
lands, and `CONTEXT.md` is a lone concept at the repository root. That is enough to be useful and
honest; claiming more would mean adding frontmatter to files this decision deliberately excludes.

## Consequences

- **A new ADR, research note or glossary page starts with a frontmatter block.** `type` is required;
  `status` is written rather than defaulted. A document that forgets it is not rejected by anything —
  nothing parses these files — so this is a convention that lives or dies by being written down here
  and in `CLAUDE.md`.
- **Amending a document means updating `generated.at`.** It records the last meaningful change to
  the content. An edit that leaves it stale is a small lie of exactly the kind OKF exists to stop.
- **Superseding an ADR now touches two places:** the superseded ADR's own **Status:** line and its
  OKF `status`, which becomes `deprecated`.
- **`docs/spec/` is untouched,** so every `BUILD-`/`SIGN-` identifier, every traceability table and
  every citation from a test resolves exactly as before. Nothing in this change can break a build.
- **#137 inherits one constraint,** point 5, and is otherwise free to choose any shape it likes.
- **Reversal is deleting frontmatter blocks.** There is no tooling to uninstall, no dependency to
  remove and no generated artefact to clean up.
