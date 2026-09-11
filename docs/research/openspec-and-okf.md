# Research — OpenSpec and OKF: what they are, and whether they overlap

**Question.** The repository owner wants persistent specification files (not merely a design change
log) and is considering **OpenSpec** for that, with **OKF documentation** where OpenSpec does not
fit. Do the two work well together, or do they overlap too much?

**Date checked: 2026-09-11.** Every version number, release date and commit count below was read on
that date and will drift.

**Sources.** Primary only, except where marked *(secondary)*: the projects' own repositories on
`github.com`, their specification and documentation files read from `raw.githubusercontent.com`, the
npm registry metadata for the published CLI, and Google Cloud's own announcement post. No blogs, no
forum answers.

**Source limitations in this environment.** `openspec.dev` and `open-specification.org` are both
blocked by the network egress proxy, as is `okf.md`. The GitHub REST API is not reachable for
repositories not attached to this session, so repository metadata (licence, stars, commit dates) was
read from the rendered repository pages rather than the API. Where that matters it is said inline.

This is a research note, not a decision. It names trade-offs; it does not pick for the owner, except
in the clearly-labelled [Recommendation](#recommendation), which is an argument, not a ruling.

---

## Answer

**The two barely overlap, because they are not the same kind of thing.** OpenSpec is a *workflow
over behavioural requirements* — a directory convention plus a CLI plus slash commands that move a
change from proposal to implemented to archived. OKF is a *file format* — markdown with YAML
frontmatter, plus conventions for indexes, cross-links, provenance and freshness — with no workflow,
no lifecycle of proposals, and no opinion about requirements at all. One governs *how a change to
behaviour gets agreed and recorded*; the other governs *how a body of knowledge describes itself to
an agent that has to traverse it*. Adopting both is coherent.

**Identities, established rather than assumed:**

- **OpenSpec** is [`Fission-AI/OpenSpec`](https://github.com/Fission-AI/OpenSpec), "Spec-driven
  development (SDD) for AI coding assistants". MIT, published to npm as
  [`@fission-ai/openspec`](https://registry.npmjs.org/@fission-ai/openspec), latest **1.13.0**
  released **2026-09-09**, requiring **Node.js ≥ 20.19.0**. There are two unrelated homonyms — an
  open-source *hardware* specification effort at `open-specification.org`, and general "open spec"
  usage in the OpenAPI orbit — neither of which is a tool an Android repository would adopt. The
  AI-coding-assistant one is plainly the one meant. Every other `openspec` repository the search
  surfaced (`iahliu`, `papiguy`, `khoantd`, `cipengxu`, `Minidoracat/OpenSpec-tw`, `ZeeBJ/OpenSpec-7D`)
  carries the same description and is a fork or localisation of the Fission-AI project.
- **OKF** is almost certainly Google Cloud's **Open Knowledge Format**, specified at
  [`GoogleCloudPlatform/open-knowledge-format`](https://github.com/GoogleCloudPlatform/open-knowledge-format),
  currently **v0.2**, Apache-2.0. It is *not* the Open Knowledge Foundation, and it is *not* Diátaxis,
  arc42, or the Grand Unified Theory of Documentation — no evidence connects those initials to any
  of them. The candidates and the evidence are set out in [What OKF is](#what-okf-is-or-what-it-might-be),
  because the ambiguity is real and the owner should confirm rather than take this on trust.

**The overlap that does exist is narrow, and it is about lifecycle vocabulary, not content.** Both
have an answer to "is this current?" — OpenSpec answers it structurally (a file under `changes/` is
proposed, a file under `specs/` is true, a folder under `changes/archive/` is history), OKF answers
it in frontmatter (`status: draft | stable | deprecated`, plus `stale_after`). Both have an answer
to "what changed here?" — OpenSpec's dated archive folders, OKF's `log.md`. Applying both to the
*same* file makes a file with two answers to the same question, which is how they drift apart.

**The overlap that actually threatens this repository is not OpenSpec-versus-OKF at all. It is
OpenSpec versus `docs/spec/`.** Both want to own the durable statement of what the software does,
and they identify requirements incompatibly: `docs/spec/build.md` uses stable short identifiers
(`BUILD-030`) that a traceability table maps to test files, while an OpenSpec requirement is
identified by its heading text and renaming one is a first-class delta operation
(`## RENAMED Requirements`). That is the question worth answering before either adoption.

---

## What OpenSpec is

### Artifacts and where they live

`openspec init` creates ([CLI reference](https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/docs/cli.md),
read 2026-09-11):

```
openspec/
├── specs/              # Your specifications (source of truth)
├── changes/            # Proposed changes
└── config.yaml         # Project configuration
```

plus tool-integration files for whichever assistants were selected — for Claude Code, skill files
under `.claude/skills/`.

`specs/` is organised by domain, one `spec.md` per capability
([Concepts](https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/docs/concepts.md)):

```
openspec/specs/
├── auth/
│   └── spec.md
├── payments/
│   └── spec.md
└── ui/
    └── spec.md
```

A change is a folder holding everything about one unit of work:

```
openspec/changes/add-dark-mode/
├── proposal.md           # Why and what
├── design.md             # How (technical approach)
├── tasks.md              # Implementation checklist
├── .openspec.yaml        # Change metadata (optional): schema, created, skip_specs, retire_capabilities
└── specs/                # Delta specs
    └── ui/
        └── spec.md       # What's changing in ui/spec.md
```

### Lifecycle

Three states, expressed as location rather than metadata
([Overview](https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/docs/overview.md)):

1. **Proposed** — a folder under `openspec/changes/`, containing a *delta* spec rather than a whole
   one: `## ADDED Requirements`, `## MODIFIED Requirements`, `## REMOVED Requirements`,
   `## RENAMED Requirements`. "You describe the diff, not the destination."
2. **Active** — the same folder, with `tasks.md` checkboxes being ticked as the assistant implements.
3. **Archived** — `openspec archive` merges the delta into `openspec/specs/` and moves the folder to
   `openspec/changes/archive/<date>-<name>/`.

The ordering `proposal → specs → design → tasks` is explicitly described as "enablers, not gates":
any artifact may be revisited at any time, and nothing locks.

### Spec format

Plain markdown, with structure the validator enforces. From a real spec in the project's own
repository ([`openspec/specs/cli-validate/spec.md`](https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/openspec/specs/cli-validate/spec.md),
read 2026-09-11):

```markdown
# cli-validate Specification

## Purpose
Define `openspec validate` behavior for validating changes and specs …

## Requirements
### Requirement: Validation SHALL provide actionable remediation steps
Validation output SHALL include specific guidance to fix each error…

#### Scenario: No deltas found in change
- **WHEN** validating a change with zero parsed deltas
- **THEN** show error "No deltas found" with guidance:
```

Note what is absent: **no requirement identifier.** A requirement is named by its heading text, and
its normative body is expected to carry `SHALL` or `MUST` (a missing keyword is a warning in normal
mode and a failure under `--strict`). That the delta vocabulary includes `RENAMED` confirms the
heading text *is* the identity — rename it and you have to say so.

### What OpenSpec claims to own

| Concern | Owned? | Where |
|---|---|---|
| Behavioural requirements | **Yes** — this is the core claim | `openspec/specs/<capability>/spec.md` |
| Acceptance scenarios | **Yes** | `#### Scenario:` blocks inside a requirement |
| Rationale for a change | **Yes** | `proposal.md` (`## Why`, `## What Changes`) |
| Technical approach / design decision | **Yes**, per change | `design.md` |
| Implementation plan | **Yes** | `tasks.md` |
| Change history | **Yes** | `changes/archive/<date>-<name>/` |
| Architecture decision records | **Partly** — `design.md` is per-change and gets archived with it; there is no standing ADR corpus |
| Tutorials, how-to guides, reference docs | **No** | out of scope |
| Traceability from a requirement to a test | **No** | see below |

`openspec validate` checks *structure*, not truth: that a change has at least one delta (unless
`.openspec.yaml` sets `skip_specs: true`), that required sections are present, that scenarios use
`#### Scenario:` headers, that a requirement has body text before its scenarios, and — under
`--archived` — that every archived change has all its `tasks.md` boxes ticked. **Nothing checks that
a requirement has a test.**

---

## What OKF is (or: what it might be)

The acronym is genuinely ambiguous. Three candidates, strongest first.

### Candidate 1 — Google Cloud's Open Knowledge Format (strongest, and near-certain)

[`GoogleCloudPlatform/open-knowledge-format`](https://github.com/GoogleCloudPlatform/open-knowledge-format),
Apache-2.0, 395 stars, 6 commits on `main`, most recent **2026-08-21** (repository page, read
2026-09-11). Announced by Google Cloud on **2026-06-12**
([How the Open Knowledge Format can improve data sharing](https://cloud.google.com/blog/products/data-analytics/how-the-open-knowledge-format-can-improve-data-sharing)).

Why this is the one meant: it is a *documentation* format made of markdown files in a git
repository, it is recent enough to be the thing someone read about this summer, and the phrase "OKF
documentation" is exactly what its own README describes — "knowledge curation becomes a normal
software-engineering activity".

From the specification itself
([`SPEC.md`](https://raw.githubusercontent.com/GoogleCloudPlatform/open-knowledge-format/main/SPEC.md),
v0.2, read 2026-09-11):

> The format is intentionally minimal: a directory of markdown files with YAML frontmatter. There is
> no schema registry, no central authority, and no required tooling. If you can `cat` a file, you
> can read OKF; if you can `git clone` a repo, you can ship it.

**Artifacts.** A **bundle** is a directory tree. Every non-reserved `.md` file is a **concept**. Two
filenames are reserved at any level: `index.md` (a directory listing, for progressive disclosure)
and `log.md` (a date-grouped update history, newest first).

```
path/to/bundle/
  index.md                      # Optional. Directory listing for progressive disclosure.
  log.md                        # Optional. Chronological history of updates.
  <concept>.md                  # A concept at the bundle root.
  <subdirectory>/
    index.md
    <concept>.md
```

**Frontmatter.** Exactly one key is required — `type`, an uncontrolled string such as
`BigQuery Table`, `Metric`, `Playbook`, `Reference`. Recommended: `title`, `description`, `resource`,
`tags`. Optional families added in v0.2: provenance (`sources`), trust (`generated`, `verified`,
from which consumers derive an unverified / machine-confirmed / human-reviewed tier), lifecycle
(`status: draft | stable | deprecated`, absent meaning `stable`; and `stale_after`, an absolute
instant), and the `Attested Computation` machinery.

**Lifecycle.** Per-document, in frontmatter, not per-directory: `draft` → `stable` → `deprecated`,
with `deprecated` meaning "kept for links and history; no longer current". There is no proposal
state and no archive location.

**Conformance is deliberately permissive.** A bundle conforms if every non-reserved `.md` file has
parseable frontmatter with a non-empty `type`, and the reserved files follow their shapes. Consumers
"MUST NOT reject a bundle because of" missing optional fields, unknown `type` values, unknown extra
keys, **broken cross-links**, or missing `index.md` files.

**What it claims to own:** the on-disk shape of a knowledge corpus, its self-description, its
traversal (markdown links as untyped graph edges, bundle-relative `/`-prefixed paths recommended),
its provenance and its freshness. **What it explicitly does not claim** (§1 Non-goals): a fixed
taxonomy of concept types, storage or serving infrastructure, or replacing domain-specific schemas —
"OKF *references* them; it does not subsume them."

**The honest caveat.** OKF's worked examples are data-catalog knowledge — tables, datasets, metrics,
playbooks about pipelines. Nothing in the format is specific to that, and the README lists Obsidian,
Notion, MkDocs and Hugo as existing consumers, but a reader should know that "documentation
framework for a software project" is a use it permits rather than a use it was written for.

### Candidate 2 — The Open Knowledge Foundation

The nonprofit ([Wikipedia](https://en.wikipedia.org/wiki/Open_Knowledge_Foundation), *(secondary)*),
known for CKAN, Frictionless Data and the Open Definition. It owns open *data* standards, not a
documentation approach. If the owner means this, the request would be about publishing data openly,
which does not match "where OpenSpec is not a good fit but a document is still warranted". Listed
for completeness; the evidence is against it.

### Candidate 3 — `okf.md`, "Open Knowledge Format — An Annotated Guide"

A site surfaced in search as an annotated guide to the same Google Cloud format. **Unreachable from
this environment** — `okf.md` is blocked by the egress proxy — so its authorship and its
relationship to the Google specification could not be verified. If the owner has been reading
`okf.md` rather than `SPEC.md`, it is worth checking which version it annotates: v0.2 made two
breaking changes against v0.1 (`timestamp` superseded by `generated.at`, and the body
`# Citations` list superseded by frontmatter `sources`).

### What OKF is *not*

Neither [Diátaxis](https://diataxis.fr/) (tutorials / how-to / reference / explanation) nor
[arc42](https://arc42.org/) (a twelve-section architecture template) has any connection to these
initials, and a targeted search for OKF as a documentation framework alongside them returned nothing
linking them. If what the owner actually wants is "a scheme for deciding what kind of document this
is", Diátaxis is that and OKF is not — OKF would happily carry a Diátaxis-shaped corpus, since
`type:` is producer-defined and could be `Tutorial`, `How-to`, `Reference` or `Explanation`.

**This is the one thing in this note worth confirming with the owner before acting on it.**

---

## Overlap

Concretely, the artifact types both would want to own:

| Artifact | OpenSpec's claim | OKF's claim | What goes wrong if both are adopted unmodified |
|---|---|---|---|
| **A markdown file stating durable truth** | `openspec/specs/<cap>/spec.md`, structured `## Purpose` / `## Requirements` / `### Requirement:` / `#### Scenario:` | any `.md` concept with `type` frontmatter | Little, *if they are different files*. If you try to make spec files OKF-conformant, you are adding frontmatter to a file `openspec archive` machine-rewrites on every merge. The frontmatter survives (nothing in OpenSpec strips it) but nothing in OpenSpec maintains it either, so `generated.at` and `status` go stale silently — which is precisely the failure OKF v0.2 exists to prevent. |
| **Currency / lifecycle state** | structural: `changes/` = proposed, `specs/` = true, `changes/archive/` = history | frontmatter: `status: draft \| stable \| deprecated`, `stale_after` | Two sources of truth for the same question. A file under `openspec/changes/` is by construction not-yet-true, but OKF's default for an absent `status` is **`stable`**. An OKF consumer pointed at the whole tree would read in-flight proposals as settled fact. |
| **Change history** | dated folders under `changes/archive/` | `log.md` at any level | Duplicated, and they will disagree. OpenSpec's archive is written by a command; `log.md` is written by whoever remembers. |
| **Index / navigation** | `openspec list`, `openspec view` (computed on demand) | `index.md` files (materialised on disk) | Benign duplication, but `index.md` is a reserved filename in OKF and would sit inside a directory whose contents OpenSpec rewrites. |
| **Rationale for a decision** | `design.md`, per change, archived with it | a concept of any `type` | This is the interesting gap, not a clash — see [Complementarity](#complementarity). |

**The sharp version.** The genuine collision is not between the two systems; it is **applying OKF to
OpenSpec's own directory**. Keep OKF out of `openspec/` and the conflict is gone. Both are markdown
in git; they coexist the way `docs/` and `src/` coexist.

**The other overlap, the one that matters more.** OpenSpec's `openspec/specs/` and this
repository's `docs/spec/` are the same artifact, built two different ways. That is
[Fit with this repository](#fit-with-this-repository).

---

## Complementarity

What each covers that the other leaves out:

**OpenSpec covers, OKF does not:**

- Proposal-before-code as an enforced shape (`proposal.md` with `## Why` and `## What Changes`).
- Requirements with acceptance scenarios in a validated grammar.
- The delta vocabulary — `ADDED` / `MODIFIED` / `REMOVED` / `RENAMED` — which is how you specify a
  change to a system you have not fully documented. OKF has no concept of a diff at all.
- An implementation checklist tied to the requirements it implements.
- A CLI that can fail a build (`openspec validate --all --json` is documented for CI).
- Slash-command integration with 30+ assistants, which is how an agent gets pointed at the right
  artifact at the right moment.

**OKF covers, OpenSpec does not:**

- **Everything that is not a behavioural requirement.** Research notes, architecture decisions,
  operational playbooks, glossaries, reference material. OpenSpec's `design.md` is per-change and is
  archived away when the change lands; OKF has no such gravity.
- **Provenance.** `sources`, with per-source credibility signals (`author`, `usage_count`,
  `last_modified`). For this repository's research notes — whose whole value is that every claim is
  traced to a primary source — that is a direct fit for something currently done in prose.
- **Trust tiers.** `generated: { by, at }` and `verified: [...]`, with the `human:<id>` actor prefix
  distinguishing human-reviewed from machine-confirmed content. In a repository where an agent
  writes most documents, "who checked this" is a real question and nothing currently records it.
- **Freshness.** `stale_after` as an absolute instant. Each research note here opens with "Date
  checked: …" and a warning that versions drift; `stale_after` is that sentence made machine-readable.
- **Progressive disclosure.** `index.md` per directory, so an agent can see what exists without
  loading everything.
- **A graph, not just a tree.** Cross-links as untyped edges, with broken links explicitly tolerated
  as "not-yet-written knowledge".

Put plainly: **OpenSpec governs the front half of the work (agreeing what to build); OKF governs the
back half (making what you learned findable and trustable afterwards).** The owner's instinct —
OpenSpec for specifications, OKF where a document is warranted but is not a specification — is the
division the two projects' own scopes suggest.

---

## Fit with this repository

What is here today:

- `docs/spec/build.md` and `docs/spec/signing.md` — requirement identifiers (`BUILD-001`,
  `SIGN-010`), explicit **invariants**, a per-requirement `auto` / `manual` marking, and a
  **Traceability** table mapping identifier ranges to test files, closing with a count
  ("32 requirements, 27 `auto` and 5 `manual`").
- `docs/adr/0001-release-signing-with-a-stable-keystore.md`.
- `docs/research/` — three notes, this being the fourth.
- `docs/agents/issue-tracker.md`.
- ai-sdlc's house rules: specification before code, a failing test watched fail, documentation
  reconciled in the same pull request, and *"Enforced: the spec↔test traceability gate"*.
- The `dev` agent: "Every behaviour gets a requirement identifier."

There is **no `CONTEXT.md`** in this repository (checked 2026-09-11); the brief named one, so either
it is planned or it belongs to another repository.

### OpenSpec against `docs/spec/`: replace, wrap, or parallel?

**Parallel is not an option worth taking.** Two directories both claiming to say what the software
does is the failure mode the house rules call "a design contract that lags the code".

**Replacing is a real migration, and the cost is concentrated in one place: identifiers.** The
traceability table exists because `BUILD-030` is a stable handle a test can be mapped to. OpenSpec
has no such handle — a requirement is its heading text, and `RENAMED` exists as a delta operation
precisely because that text is the identity. Three consequences:

1. The existing Traceability tables cannot be carried across unchanged. A table keyed on
   "Requirement: Duration formatting renders m:ss" is a table that breaks when someone improves the
   wording.
2. ai-sdlc's traceability gate would have to be taught a second spec location and a second
   identifier scheme, and **the rule for that is not negotiable in this repository**: gates live in
   ai-sdlc, not here, so this is an issue filed against `derekwinters/ai-sdlc`, not a change made
   locally. (ai-sdlc's own specification could not be read from this environment — its repository is
   not reachable here — so what the gate actually parses is unverified; treat this paragraph as the
   shape of the problem, not its detail.)
3. The `auto` / `manual` distinction, and the honest counting of manual requirements, has no OpenSpec
   equivalent. `#### Scenario:` blocks are acceptance criteria, but nothing records whether a scenario
   is executed or merely asserted. That distinction is doing real work in `build.md`, where 13 of 17
   requirements are facts about configuration that no unit test can reach.

**Wrapping is the cheap middle.** Keep `docs/spec/*.md` with its identifiers and traceability tables
as the durable specification, and use OpenSpec only for the *change* half — `proposal.md`,
`design.md`, `tasks.md` — skipping `openspec/specs/` entirely via `.openspec.yaml`'s
`skip_specs: true`, which the CLI documents for exactly this ("pure refactors, tooling, or docs
work"). This gets the plan-before-code workflow and the assistant integration without a second
requirements corpus. The cost: `skip_specs` on every change is using the tool against its grain, and
`openspec archive` then merges nothing, so the main value proposition — specs that stay current
automatically — is the part you gave up.

**Two smaller frictions worth knowing before `openspec init` runs:**

- It writes skill files into `.claude/skills/`, which in this repository is shared ground: ai-sdlc's
  `adopt` installs skills there from the `skills:` list in `.ai-sdlc/repo-config.yml`, and the
  mattpocock skills live there too. The mattpocock skills are precedent that foreign skills coexist —
  `CLAUDE.md` says plainly that ai-sdlc's `skills-update` workflow does not manage them — so this is
  a documentation obligation (say in `CLAUDE.md` who owns which skill and that `openspec update`
  rewrites its own) rather than a collision.
- The CLI phones the npm registry on `openspec update` to check for a newer version, and collects
  anonymous telemetry (command names and version only). Both are off when `CI` is set; both can be
  disabled with `OPENSPEC_NO_UPDATE_CHECK` / `OPENSPEC_TELEMETRY=0` / `DO_NOT_TRACK=1`.

### OKF against `docs/`

Much cheaper, because OKF is additive by construction. Making `docs/research/` an OKF bundle means:
adding `---\ntype: Research Note\n---` and a few recommended keys to the top of each existing note,
and optionally an `index.md`. Everything else — the prose, the citation style, the "Date checked"
convention — is already what OKF asks for. Nothing in the repository parses these files, so nothing
breaks. `status` and `stale_after` would formalise conventions the notes already state in English.

The honest counter: with three research notes, one ADR and two specification pages, **there is no
navigation problem to solve.** Progressive disclosure and trust tiers pay off on a corpus of
hundreds of agent-written documents. Adopting the frontmatter now is cheap insurance; adopting the
whole apparatus now is ceremony.

There is also a real question OKF does not answer and `docs/adr/` does: OKF gives `type: Decision`
no more meaning than `type: Anything`, because the taxonomy is deliberately uncontrolled. OKF would
*carry* the ADRs; it would not tell you how to write one.

---

## Tooling and maintenance

| | **OpenSpec** | **OKF** |
|---|---|---|
| Kind | CLI tool + workflow + conventions | Specification only |
| Repository | [`Fission-AI/OpenSpec`](https://github.com/Fission-AI/OpenSpec) | [`GoogleCloudPlatform/open-knowledge-format`](https://github.com/GoogleCloudPlatform/open-knowledge-format) |
| Licence | MIT (npm metadata, and repository `LICENSE`) | Apache-2.0 (repository page) |
| Version | **1.13.0**, published **2026-09-09** | **v0.2** of the format |
| Runtime | **Node.js ≥ 20.19.0** (`engines` in package metadata) | **None required.** The optional reference agent and visualizer are **Python 3.13** |
| Install | `npm install -g @fission-ai/openspec@latest`; also pnpm, yarn, bun, nix | nothing to install |
| Release cadence | 48 versions since first publish **2025-09-06**; 1.6.0 through 1.13.0 all landed between 2026-07-10 and 2026-09-09 — roughly weekly | 6 commits total on `main`, 2026-08-14 to **2026-08-21**; format announced 2026-06-12 |
| Assistant-specific? | No, but assistant-*aware*: 40+ tool IDs for `openspec init`, generating skills/commands per tool. Works via plain natural language where there are no slash commands | Entirely agnostic — "not tied to any particular agent, framework, model provider, or serving system" |
| Dependencies | 10 runtime packages (`commander`, `zod`, `yaml`, `chalk`, `ora`, `fast-glob`, `diff`, `cross-spawn`, two `@inquirer/*`) | none |
| Telemetry | Anonymous command names + version; disabled in CI; opt-out documented | none |
| Governance risk | Single-vendor startup project, very fast-moving — the README itself flags a *rebuilt* workflow (`/opsx:*`) superseding the previous one | Google Cloud-published; v0.2 already made two breaking changes to v0.1 fields within three months |

**Read both cadences as warnings, in opposite directions.** OpenSpec is maintained to the point of
churn: a weekly minor release and a recently rebuilt command surface mean the workflow you adopt in
September is not certainly the workflow you run in March. OKF is the inverse — six commits, a format
three months old, already at its second version with breaking renames, and no tooling that would
tell you if your files stopped conforming. Neither is a reason not to adopt; both are reasons to pin
what you depend on and to say in `CLAUDE.md` which version this repository targets, exactly as
`.ai-sdlc/adoption.md` already does for ai-sdlc.

---

## Recommendation

**Together. The overlap between OpenSpec and OKF is small enough to manage with one rule, and the
two cover complementary halves of the problem.**

The rule: **OKF frontmatter never goes inside `openspec/`.** OpenSpec owns that directory and
rewrites files in it; anything OKF wrote there would rot unattended. Everything under `docs/` that is
*not* a specification — research notes, ADRs, agent-facing documents — is where OKF earns its keep,
and that is precisely the territory OpenSpec declines.

But the adoption order matters more than the compatibility question, and here the recommendation is
sharper:

1. **Settle OpenSpec versus `docs/spec/` first, as its own issue, before adopting either.** This is
   the only decision with a migration cost, and it is a design decision the house rules say an agent
   must not make alone. The specific question to answer is: *does this repository give up stable
   requirement identifiers and the `auto`/`manual` traceability table in exchange for OpenSpec's
   automatic spec maintenance?* Because ai-sdlc's traceability gate is not implemented in this
   repository, answering "yes" implies an issue filed upstream against `derekwinters/ai-sdlc` before
   any local change is worth making.
2. **Adopt OKF frontmatter incrementally and cheaply.** One required key. Add `type`, `title`,
   `description`, `status`, `generated` and `sources` to the documents under `docs/` that are not
   specifications, skip `index.md` until there is enough to index, and pin `okf_version: "0.2"` in a
   root `index.md` when one exists. This costs a few lines per file and can be reversed by deleting
   them.
3. **Do not adopt both in the same pull request.** "One issue, one branch, one pull request" is the
   house rule, and these are two unrelated decisions with different reversal costs.

### The strongest argument against this recommendation

**That OKF is the wrong tool for a software repository's documentation, and adopting it is cargo
cult.** The case is genuinely strong. OKF was written for data-catalog knowledge — its worked
examples are BigQuery tables, datasets, metrics and an income statement; its headline v0.2 feature is
*attested computation*, machinery for proving a financial number was computed the sanctioned way,
which an Android interval trainer will never use. Its benefits — progressive disclosure, trust tiers,
graph traversal — are benefits at the scale of hundreds of agent-generated concepts, and this
repository has six documents. Meanwhile the thing the owner might actually want from "a documentation
approach" — *what kind of document is this, and what belongs in it* — is exactly what OKF's non-goals
say it will not provide ("Defining a fixed taxonomy of concept types"). Diátaxis answers that
question and OKF does not, and a repository that adopts OKF hoping for it will find it has adopted a
YAML convention and still has to decide everything that mattered.

The honest response is not that this is wrong, but that it argues for adopting *less* of OKF rather
than none: the frontmatter is nearly free and makes the corpus self-describing before it grows, while
the attestation and index machinery can be left on the shelf indefinitely. If the owner wants a
framework that tells him *what to write*, he should look at Diátaxis in addition to OKF, not instead
of a decision between them — they are not competitors either.

A second, smaller objection deserves stating: **OpenSpec may be too young to build a repository's
contract on.** A project that rebuilt its entire command surface this year and ships weekly is a
project whose conventions will move under you. The mitigation is the one this repository already uses
everywhere else — pin the version, write down which version is targeted, and upgrade deliberately.

---

## Open questions

These need the owner, or a source this environment could not reach.

1. **Is "OKF" the Google Cloud Open Knowledge Format?** Everything above assumes it is, on the
   evidence in [Candidate 1](#candidate-1--google-clouds-open-knowledge-format-strongest-and-near-certain).
   If the owner has been reading `okf.md` — unreachable here — it should be checked against
   `SPEC.md`, and in particular against v0.2's breaking renames.
2. **Is the thing actually wanted a Diátaxis-style taxonomy?** "Where OpenSpec is not a good fit but
   a document is still warranted" is a question about *kinds of document*. OKF does not answer it,
   by design.
3. **What does ai-sdlc's spec↔test traceability gate actually parse?** Its specification is not
   reachable from this environment. Whether `docs/spec/` is a hard-coded path, whether the identifier
   format is fixed, and whether a second spec root is even expressible are all unknown, and all three
   decide how expensive an OpenSpec migration would be.
4. **Would `openspec/specs/` be inside or outside the traceability gate's scope?** If inside, the
   identifier problem is blocking. If outside, adopting OpenSpec means specifications that the gate
   does not check — which is a weakening of the current arrangement and should be argued for
   explicitly, not arrived at.
5. **Does the repository intend to have a `CONTEXT.md`?** None exists today; if one is planned, it is
   a natural OKF root `index.md` and the two decisions should be taken together.
