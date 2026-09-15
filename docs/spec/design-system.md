# Specification — Design system (`DS`)

The component vocabulary every screen builds from, the closed set of screen layouts a screen chooses
between, and how consistency across all of it is checked mechanically rather than by eye.

This page is the vocabulary half of what [#40](https://github.com/derekwinters/Interval-trainer-android/issues/40)
settled. The two decisions from that ticket hard enough to reverse to count as architecture — the
`:designsystem` module boundary that makes a raw Material component unreachable outside it, and the
bespoke-tokens-mapped-onto-`ColorScheme` strategy — are [ADR 0007](../adr/0007-a-designsystem-module-with-bespoke-colour-tokens.md),
not repeated here. Everything below is the vocabulary an implementing agent lifts directly:
[#33](https://github.com/derekwinters/Interval-trainer-android/issues/33) is the v1 specification
that uses it.

Colour is a second boundary worth stating up front: [`docs/spec/cues.md`](cues.md) (`CUE-030`,
`CUE-031`) fixes what each interval kind's colour *means* — work is green, recovery is yellow,
warm-up and cool-down share a neutral, red is not used for a kind. This page fixes the *tokens*: the
names the app's components read, and the roles those tokens also satisfy on Material's own
`ColorScheme`. Neither page restates the other's decision.

Most of what is specified here does not exist in the build yet. `:designsystem` now exists
(`BUILD-002`, `BUILD-019`) with `material3` declared `implementation`, and the token layer
below — the colour tokens, the spacing scale and the three timer typographic roles, assembled into
a root `AppTheme` — is implemented. `:app` does not yet depend on `:designsystem`, so the
compile-time enforcement `DS-090` describes does not yet apply to it, and no component vocabulary
(`PrimaryButton`, `ScreenHeader`, and the rest of §1–3), no screen layout (§8), and no Robolectric
dependency exist yet. This page describes what the rest of this vocabulary becomes when
[#33](https://github.com/derekwinters/Interval-trainer-android/issues/33) and later implementation
tickets do that work, in the same way `docs/spec/cues.md` specifies cue selection with no `:core`
module yet to hold it.

---

## Invariants

> **Invariant — a screen does not arrange itself; it fills a layout's named slots.** Position on
> screen for a header, a list, a counter or a dialog's actions is a property of the layout in §8, not
> of the care taken while writing an individual screen. A screen composable that lays out its own
> header, its own spacing and its own action row instead of choosing one of §8's layouts has picked
> the wrong shape regardless of whether the result looks correct.

> **Invariant — a raw Material component is reachable only from inside `:designsystem`.** Stated here
> as the vocabulary-level consequence of [ADR 0007](../adr/0007-a-designsystem-module-with-bespoke-colour-tokens.md):
> `:app` calls `PrimaryButton`, `ScreenHeader`, `AlertDialog`'s wrapper and the rest of this page's
> vocabulary, and never constructs `androidx.compose.material3.Button` or `TopAppBar` itself, on any
> of the six v1 screens including the three built from stock Material components (§7).

> **Invariant — adding a component or a layout is a specification change, not a call-site decision.**
> An implementing agent that needs a shape this page does not name amends this page first, with the
> reasoning for the addition, rather than improvising a one-off at the call site. §8.4 states the same
> rule for layouts specifically, because it was decided first and carries its own bar.

---

## 1. Buttons and actions

- **DS-001** `PrimaryButton` is filled, `work`-green fill, dark ink text, one fixed padding, weight
  700. It is the single primary action per screen — Start, Save.
- **DS-002** `SecondaryButton` has a dashed border, `chip` fill, `dim` text. It is used for
  optional or additive actions — `+ Interval`, `+ Rounds…`.
- **DS-003** A destructive action is rendered in red, and is reserved for a confirmed or
  saved-state deletion — preset deletion, the stop-workout confirmation.
- **DS-004** Deleting an unsaved row inside an editor is plain: no confirmation dialog, no red. It is
  not a destructive action in the `DS-003` sense, because nothing saved is lost.
- **DS-005** An icon button in the **status** role is drawn at approximately 36–40dp, with a 48dp
  minimum touch target regardless of the drawn size.
- **DS-006** An icon button in the **transport** role (running-screen controls) is drawn at
  approximately 50dp, with the same 48dp minimum touch target.
- **DS-007** An icon button in the **FAB** role is 56dp.
- **DS-008** `CountStepper` is a `−`/`+` mini-stepper used for round count only, never for a
  duration. It is drawn small — 26px in the settled prototype — and its tap area still meets the
  48dp minimum touch target regardless of its drawn size, exactly as `DS-005`–`007`.
- **DS-009** Duration is entered with a scroll picker — a flick-scrub drum for minutes and seconds,
  reusing the running screen's fade/shrink treatment — never with steppers, typed digits, or chips.
  This supersedes the increment/decrement wording in [#29](https://github.com/derekwinters/Interval-trainer-android/issues/29)
  and in [#40](https://github.com/derekwinters/Interval-trainer-android/issues/40)'s own original
  text, both of which predate this decision.

## 2. Dialogs

- **DS-010** `AlertDialog` is the only overlay component in the v1 vocabulary. No bottom-sheet
  component exists; one is added only when a feature actually needs it, per the invariant that an
  addition is a specification change.
- **DS-011** A dialog's cancel action sits left, its affirmative action sits right, and a destructive
  action inside a dialog is rendered in red text.

## 3. Screen scaffolding

- **DS-020** `ScreenHeader` is a fixed header row: a title top-left, exactly one trailing action
  (an icon or a text button), and an optional page-level FAB (home's "new preset").
- **DS-021** Every one of the six v1 screens uses `ScreenHeader`, including the three built from
  stock Material 3 components in §7 — never a stock `TopAppBar`.

## 4. Timer typography

- **DS-030** `timer.large` is the large countdown display: JetBrains Mono, tabular figures.
- **DS-031** `timer.stat` is the round counter and the total-remaining display: JetBrains Mono,
  tabular figures.
- **DS-032** `timer.inline` is an inline duration shown in a list row: JetBrains Mono, tabular
  figures.
- **DS-033** These three are the only typographic roles a timer value may render in. A fourth is a
  specification change, not a call-site font size.

## 5. Spacing

- **DS-040** The spacing scale has a 4dp base: `spacing.xs` (4), `sm` (8), `md` (12), `lg` (16),
  `xl` (20), `xxl` (24). It absorbs the ad hoc padding already present in the settled prototypes
  ([#29](https://github.com/derekwinters/Interval-trainer-android/issues/29)); no screen introduces a
  padding value outside this scale.

## 6. Colour tokens

- **DS-050** The bespoke semantic colour tokens are `work`, `recovery`, `neutral`, `bg`, `fg`,
  `dim`, `line`, `chip`, and a destructive red. `:designsystem`'s own components — everything in
  §1–5 — read these directly, never a raw colour value. `CUE-030` and `CUE-031` already fix what
  `work`, `recovery` and `neutral` mean for an interval kind; this is the name the token layer gives
  each.
- **DS-051** The same palette is also mapped onto Material 3's `ColorScheme` roles: `surface`←`bg`,
  `onSurface`←`fg`, `primary`←`work`/accent, `outline`←`line`, `surfaceVariant`←`chip`,
  `error`←destructive. This is what keeps the root `MaterialTheme` — required regardless, per
  [ADR 0004](../adr/0004-jetpack-compose-with-material-3.md) — off Material's baseline defaults. See
  [ADR 0007](../adr/0007-a-designsystem-module-with-bespoke-colour-tokens.md) for why this mapping is
  architecture rather than a vocabulary choice.
- **DS-052** No raw colour literal and no hard-coded dimension appears outside this token layer and
  the spacing scale in §5. There is no lint or static rule enforcing this in v1 (§9, `DS-091`); it is
  a review-time judgement call stated here so it is not silently assumed to be automated.

## 7. Screen split

- **DS-060** Home, the preset editor and the running screen are built from the bespoke vocabulary in
  §1–6.
- **DS-061** Summary, settings and first-run are built from stock Material 3 components —
  `ListItem`, `Switch`, plain `Scaffold` body content — styled only by the tokens in §6. No bespoke
  mockup was or will be commissioned for these three.
- **DS-062** All six screens still use `ScreenHeader` (`DS-020`–`021`) regardless of which side of
  this split they are on.

## 8. Screen layouts

The layout set is closed, and it lives here rather than in code comments or convention. A screen
chooses one of the three layouts below; it does not arrange its own header, spacing and action
placement, per this page's first invariant.

- **DS-070** There are exactly three named layouts for v1: **list**, **full-bleed**, and **form**.
  Every screen's root composable is one of these three, and the structural check in `DS-101` asserts
  it directly rather than through a hard-coded list, so a legitimate future addition under `DS-074`
  does not fight the tooling.

### 8.1 List layout

- **DS-071** Slots: `ScreenHeader`, scrollable list content, an optional bottom action or FAB.
- **DS-072** Use it when the screen's primary content is a collection of like items browsed
  top-to-bottom and acted on individually. Screens: home (the preset list), the preset editor,
  settings, summary.

### 8.2 Full-bleed layout

- **DS-073** Slots: a free composition, with no standard list scaffold.
- **DS-074** Use it when the screen's whole purpose is one focal element that a header-and-list frame
  would compete with rather than support — a running countdown occupying most of the screen. Screen:
  the running screen.

### 8.3 Form layout

- **DS-075** Slots: `ScreenHeader` (optional), centered content, a single primary action.
- **DS-076** Use it when the screen asks the user to read or confirm something and take exactly one
  next step. Screen: the first-run explanation.

### 8.4 Adding a layout

- **DS-077** A fourth layout costs one entry in this section, carrying both its slots and the
  reasoning for when to use it and why an existing layout does not serve — not only its shape. There
  is no approval ceremony beyond writing that entry.
  *(manual: an editorial rule governing future changes to this page, not a fact about code.)*
- **DS-078** The bar for a new layout is the value it pins down for an implementing agent, not the
  cost of adding one, which is deliberately kept low. A layout's own entry is written so that reusing
  an existing layout is the obviously attractive choice whenever one would serve; reuse is not
  mandated, because a documented exception process is machinery this project does not need.
  *(manual: as `DS-077`.)*

## 9. Enforcement

What actually checks the vocabulary above, given no emulator and no connected device — the same
constraint [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md) built `:core` around.

### 9.1 Adopted

- **DS-090** The `:designsystem` module boundary, with `material3` declared `implementation`, is the
  structural enforcement mechanism: a raw Material component used outside `:designsystem` is a
  compile error. The module and the dependency declaration exist (`BUILD-019`); the enforcement
  itself has nothing to bite on yet, since `:app` does not depend on `:designsystem` until a later
  issue wires that up. *(manual: a module boundary, per [ADR 0007](../adr/0007-a-designsystem-module-with-bespoke-colour-tokens.md);
  not something a test of this page asserts, the same way `CUE-003` treats its own module
  boundary.)*
- **DS-091** JVM assertions over the Compose semantics tree, run under Robolectric, check every
  clickable node's touch target against the 48dp minimum (`DS-005`–`008`), a component's position in
  the root across the screens that share it, and that every icon-only control has a non-empty content
  description. They quantify over every screen rather than naming one, and need no screenshot.
  *(auto, once `:designsystem` and its Compose test dependencies exist: `DesignSystemConsistencyTest.kt`,
  to live at `designsystem/src/test/kotlin/com/derekwinters/intervaltrainer/designsystem/DesignSystemConsistencyTest.kt`.)*
- **DS-092** Robolectric's `android-all` jar is pre-fetched and cached in continuous integration with
  `robolectric.offline` set, never vendored as a committed jar in this repository and never fetched
  live inside `./gradlew test`. This is what keeps `DS-091` compatible with
  [`docs/spec/build.md`](build.md)'s clean-checkout invariant, and settles the open question ADR 0005
  left for the v1 specification to answer. *(manual: a continuous-integration configuration fact,
  checked by the workflow having no network step for it left unaccounted.)*
- **DS-093** Every screen's root composable is asserted to be one of the three layouts named in §8.
  The assertion follows this specification's set — it does not hard-code the three names in a way
  that would need changing independently of `DS-070` — so an addition under `DS-074` does not fight
  the tooling. *(auto, once `:designsystem` exists: `DesignSystemConsistencyTest.kt`, as `DS-091`.)*
- **DS-094** The colour tokens in §6 are checked for WCAG contrast: a pure-Kotlin unit test computes
  the contrast ratio for each token pair text is rendered against and asserts it meets the
  requirement `CUE-033` states. It needs no Android runtime and runs independently of `DS-091`'s
  Robolectric dependency. *(auto, once `:designsystem` exists: `TokenContrastTest.kt`, to live at
  `designsystem/src/test/kotlin/com/derekwinters/intervaltrainer/designsystem/TokenContrastTest.kt`;
  satisfies `CUE-033`.)*
- **DS-095** The component gallery — a single `@Preview` screen showing every button variant, both
  icon-button roles, `CountStepper`, the duration picker and a dialog together — is not a screenshot
  target. It is the fixture `DS-091` and `DS-093`'s tests run their assertions against, so one
  gallery composable is what every screen-spanning assertion actually inspects.
  *(manual: a test-fixture design fact, not itself an assertion.)*

### 9.2 Explicitly not adopted for v1

- **DS-096** Screenshot-diff tooling — Paparazzi, Roborazzi, or Google's Compose Preview Screenshot
  Testing plugin — is not adopted for v1. Each proves a screen has not *changed*; none proves two
  screens *agree*, which `DS-091` and `DS-093` do more directly and without goldens, cross-machine
  rendering drift, or a review step that depends on a human reading an image diff.
  *(manual: an absence, per the trade-off `docs/research/ui-consistency.md` §2 and §6 records.)*
- **DS-097** Lint or detekt rules prohibiting a raw colour or dimension literal outside the token
  layer — `ForbiddenImport`, `ForbiddenMethodCall`, `MagicNumber`, or a custom Android Lint check —
  are not adopted for v1. Revisit only if the `:designsystem` module boundary (`DS-090`) is ever
  found to be leaking, not on a schedule. *(manual: an absence; see `DS-052`.)*

## 10. What remains a human judgement call

Stated plainly, per this page's introduction and per #40's own request that this not pretend to be
fully automatable. None of the four below has a test today, and none is expected to gain one under
this decision:

- **DS-098** Visual regressions across screens are not caught by any adopted tool (`DS-096`); they
  are caught by eye in review.
- **DS-099** A raw colour or a hard-coded dimension leaking outside the token layer (`DS-052`) is not
  blocked by a lint or static rule (`DS-097`); it is a review-time judgement call.
- **DS-100** Real-device touch-target feel, and whether a tone or a vibration actually reads as
  intended, remain manual verification — already named as such in
  [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md) — and this decision does not
  change that.
- **DS-101** Whether `enableAccessibilityChecks()` and the Accessibility Test Framework actually run
  under Robolectric on this stack is `docs/research/ui-consistency.md`'s own open question. It is
  untestable until `:designsystem` and its Compose test dependencies exist, and is carried forward as
  an implementation-time check on [#33](https://github.com/derekwinters/Interval-trainer-android/issues/33),
  not resolved here.

---

## Traceability

| Section | IDs | Tests |
|---|---|---|
| Buttons and actions | DS-001–009 | *(manual)* |
| Dialogs | DS-010–011 | *(manual)* |
| Screen scaffolding | DS-020–021 | *(manual)* |
| Timer typography | DS-030–033 | *(manual)* |
| Spacing | DS-040 | *(manual)* |
| Colour tokens | DS-050–052 | `ColorSchemeMappingTest.kt` (DS-051's `ColorScheme` mapping); `TokenContrastTest.kt` (DS-050–051, via DS-094, not yet written); *(manual)* DS-050, DS-052 |
| Screen split | DS-060–062 | *(manual)* |
| Screen layouts | DS-070–078 | `DesignSystemConsistencyTest.kt` (DS-070, via DS-093); *(manual)* DS-071–078 |
| Enforcement — adopted | DS-090–095 | `DesignSystemConsistencyTest.kt` (DS-091, DS-093); `TokenContrastTest.kt` (DS-094); *(manual)* DS-090, DS-092, DS-095 |
| Enforcement — not adopted | DS-096–097 | *(manual)* |
| Human judgement calls | DS-098–101 | *(manual)* |

**45 requirements, 3 `auto` and 42 `manual`.**

**The three `auto` tests this page promised still do not exist.** `:designsystem` now exists
(`BUILD-002`, `BUILD-019`), with the colour tokens, spacing scale and timer typography roles
implemented and assembled into a root `AppTheme`, and a Compose dependency is now in the build —
but Robolectric is not, so `DesignSystemConsistencyTest.kt` (`DS-091`, `DS-093`) still has no
Compose test dependency to run against, and `TokenContrastTest.kt` (`DS-094`) — the WCAG contrast
check this page names for the colour tokens — is not written either: `CUE-033` and this page both
name "the contrast requirement" without stating the numeric ratio a test would assert, and choosing
one was judged a design decision for whoever picks up `DS-094` to make deliberately rather than by
implication here. What the token layer's own implementation does add is `ColorSchemeMappingTest.kt`,
a fourth, previously-unnamed test asserting `DS-051`'s six-role mapping — not one of the three IDs
promised `auto` above, but real production code (`designSystemColorScheme()`) a wrong mapping would
make red. `DesignSystemConsistencyTest.kt` and `TokenContrastTest.kt` are still named here so the
tests that will assert `DS-091`, `DS-093` and `DS-094` have one home each rather than being invented
at implementation time, and they are still named in the future tense on purpose. A requirement
marked `auto` is a promise that a JVM test *can* assert it and *will*, not a claim that one does.

**Why the proportion is almost entirely `manual`.** Most of this page is a design fact — a fill
colour, a padding value, which slot a title sits in — the same way most of `build.md` is a
configuration fact. The three genuinely cross-screen invariants this decision found assertable
without an emulator or a screenshot are exactly the three marked `auto`: a touch target and a
screen's chosen layout, both checkable by walking the Compose semantics tree, and a contrast ratio,
checkable as pure arithmetic over the token values. Everything else — whether a button's dashed
border reads as "optional" rather than "primary", whether the duration picker's flick-scrub feels
right, whether a destructive action's red is unmistakable — is a design judgement, and `docs/spec/design-system.md`
says so rather than implying a mechanism that does not exist.
