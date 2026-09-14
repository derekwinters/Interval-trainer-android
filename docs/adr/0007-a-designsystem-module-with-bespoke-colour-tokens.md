# 7. A `:designsystem` module with bespoke colour tokens mapped onto Material 3

- **Status:** accepted
- **Date:** 2026-09-14
- **Decided by:** @derekwinters
- **Issue:** [#40](https://github.com/derekwinters/Interval-trainer-android/issues/40)
- **Research:** [`docs/research/ui-consistency.md`](../research/ui-consistency.md) (issue
  [#38](https://github.com/derekwinters/Interval-trainer-android/issues/38))
- **Specification:** [`docs/spec/design-system.md`](../spec/design-system.md), which lands with
  this decision; the screens themselves land with the v1 specification,
  [#33](https://github.com/derekwinters/Interval-trainer-android/issues/33)

## Context

[ADR 0004](0004-jetpack-compose-with-material-3.md) chose Compose with Material 3 and said plainly
that Material 3's role-based `colorScheme`, typography and shapes are "the substrate the
design-system work on [#40] builds the app's vocabulary on" and that "nothing in this ADR decides
how anything looks." [ADR 0005](0005-a-pure-jvm-core-and-a-thin-android-shell.md) split the app
into a pure-JVM `:core` and a thin Android `:app`, and the research behind it noted in passing that
"a third module for the design system fits that shape, but it is a structural decision for the
owner, not a finding." Both ADRs left the same two questions open, and both point at
[#40](https://github.com/derekwinters/Interval-trainer-android/issues/40) as where they get
answered.

Most of what #40 settles — which composable does what, where things sit, the spacing scale — is
specification vocabulary: reversible, and cheap to change by editing a table. Two of its answers are
not. Once components exist that either can or cannot see `androidx.compose.material3`, and once a
root `MaterialTheme` is either wired to the app's own palette or is not, reversing either decision
means moving real code across a module boundary or re-theming every screen. Those two belong here.

The research on [#38](https://github.com/derekwinters/Interval-trainer-android/issues/38), recorded
in [`docs/research/ui-consistency.md`](../research/ui-consistency.md), found only one mechanism that
produces true *agreement* between screens rather than merely detecting when they have drifted apart:
making the raw Material component unreachable at compile time, via a Gradle module boundary with
`material3` declared `implementation` rather than `api`. Everything else it surveyed — screenshot
diffing, lint, detekt — only detects a divergence after it has already been written. The same
research documents Google's own guidance that a value Material has no role for is added as a
`staticCompositionLocalOf` token set alongside `MaterialTheme`, not by fighting Material's own roles.

## Decision

Two decisions, both hard to reverse once screens are built against them.

### 1. The design system is its own Gradle module, `:designsystem`, and `material3` is an `implementation` dependency of it

`:designsystem` depends on `androidx.compose.material3` with the `implementation` configuration,
never `api`. Gradle's `implementation` dependencies are not exposed on a consuming module's compile
classpath, so `:app` (and any future feature module) has no compile-time access to
`androidx.compose.material3.Button`, `Switch`, or any other raw Material type — only to what
`:designsystem` chooses to expose: `PrimaryButton`, `SecondaryButton`, `ScreenHeader`, `AppTheme`,
and the rest of the vocabulary in
[`docs/spec/design-system.md`](../spec/design-system.md). A raw Material component reached for
outside `:designsystem` is a compile error, not a lint warning, a code-review comment, or a
convention someone has to remember. This is the enforcement half of #40's original question,
resolved the way the research recommended adopting first: it is the only mechanism on offer that
makes the inconsistent thing unwritable rather than detectable.

### 2. Bespoke semantic colour tokens, mapped onto Material's `ColorScheme` roles

`:designsystem`'s own components read a closed set of bespoke tokens directly — `work`, `recovery`,
`neutral`, `bg`, `fg`, `dim`, `line`, `chip`, plus a destructive red — not a Material role. This is
the app's own vocabulary: [`docs/spec/cues.md`](../spec/cues.md) (`CUE-030`, `CUE-031`) already fixed
what each interval kind's colour *means*; this fixes the names the token layer uses to say it.

A root `MaterialTheme` exists regardless of this decision — ADR 0004 requires one, Compose cannot be
used without one, and three of the six v1 screens use stock Material 3 components
([`docs/spec/design-system.md`](../spec/design-system.md) §7) that read `colorScheme` directly and
have no bespoke wrapper to intercept them. The same bespoke palette is therefore *also* mapped onto
`ColorScheme`'s own roles — `surface`←`bg`, `onSurface`←`fg`, `primary`←`work`/accent,
`outline`←`line`, `surfaceVariant`←`chip`, `error`←destructive — so that root theme never sits on
Material's baseline defaults (Material's own purple and teal) even for a moment. One palette, read
two ways: the app's own components read it as itself, and Material's stock components read it
through the roles it happens to also satisfy.

## Alternatives considered

**The design system as a package inside `:app`, not a separate module.** Cheaper to set up. Rejected
because the compile-time enforcement in Decision 1 only exists at a module boundary — the research
names this explicitly as an open question, and answers it: inside one module, Kotlin's `internal`
visibility does not stop a sibling package from importing `androidx.compose.material3` directly, and
enforcement falls back to a detekt `ForbiddenImport` rule, which is precisely the mechanism #40's own
resolution marks **skip for v1**. Choosing the package form here would have made that skip
contradict itself in the same decision.

**`material3` as an `api` dependency of `:designsystem`.** Rejected outright: an `api` dependency is
exposed on every consuming module's compile classpath, which is exactly what Decision 1 exists to
prevent. There is no version of this decision where `material3` is `api` and the enforcement claim
still holds.

**Bespoke tokens only, `MaterialTheme` left on its defaults.** Rejected because three of the six v1
screens — summary, settings, first-run — are stock Material 3 with no bespoke wrapper
([`docs/spec/design-system.md`](../spec/design-system.md) §7). Leaving `ColorScheme` unmapped means
those three screens look like a different, unstyled app next to the other three, which is the exact
defect #40 exists to prevent — and it means the root theme sits on Material's stock purple until the
first bespoke composable paints over it, which is not one look, it is two.

**`ColorScheme` roles only, no bespoke token layer** — extend `ColorScheme` with named roles instead
of a parallel vocabulary. Rejected because Material's roles (`primary`, `secondary`, `tertiary`, the
`on-` and container pairs) do not name this app's own domain — an interval kind, a spacing step, a
timer's digit style — and forcing every reference to "the work colour" through a role that means
something else in Material's own documentation is indirection bought for nothing.
`docs/spec/cues.md` already committed to naming interval kinds directly; a token layer that did not
would disagree with a specification that already shipped.

## Consequences

- **`:designsystem` does not exist yet**, the same status `:core` had when ADR 0005 was written:
  `settings.gradle.kts` includes exactly one module, `:app` (`BUILD-002`). This ADR constrains where
  design-system code is written when the module is created, on [#33](https://github.com/derekwinters/Interval-trainer-android/issues/33)
  or a later implementation ticket; it does not describe the build as it stands, and nothing here
  should be read as claiming the module, Compose, or Robolectric are wired up today.
- **`material3` moves off `:app`'s own dependency declaration.** ADR 0004's consequences list
  "`:app` gains the Compose dependencies and the Compose compiler... the Compose BOM, Material 3, the
  activity and navigation artefacts" as one bullet. This decision narrows that: `material3` is
  declared once, inside `:designsystem`, as `implementation`; `:app` depends on `:designsystem` for
  everything Material-shaped and keeps the Compose runtime, activity and navigation artefacts for
  itself. ADR 0004's dependency list is otherwise unaffected.
- **The three stock-Material screens depend entirely on the `ColorScheme` mapping for their look.**
  They have no bespoke component to carry the palette instead, so the mapping in Decision 2 is not
  optional polish — it is the only thing keeping those three screens visually of a piece with the
  other three.
- **The component gallery lives inside `:designsystem`.** It is repurposed as a test fixture per
  #40's resolution (`docs/spec/design-system.md` §8), and it previews components the module itself
  owns, so it has nowhere else to live.
- **This ADR resolves the deferrals both ADR 0004 and ADR 0005 made to #40**, for the module-boundary
  and token-strategy questions specifically. The rest of #40's vocabulary — button rules, the
  spacing scale, the closed set of screen layouts, and the enforcement mechanisms that are not
  architectural — is specification content, not architecture, and lands in
  [`docs/spec/design-system.md`](../spec/design-system.md) rather than here.
