# 4. The user interface is Jetpack Compose with Material 3

- **Status:** accepted
- **Date:** 2026-09-10
- **Decided by:** @derekwinters
- **Issue:** [#24](https://github.com/derekwinters/Interval-trainer-android/issues/24)
- **Research:** [`docs/research/ui-consistency.md`](../research/ui-consistency.md) (issue
  [#38](https://github.com/derekwinters/Interval-trainer-android/issues/38))
- **Specification:** the screens themselves land with the v1 specification,
  [#33](https://github.com/derekwinters/Interval-trainer-android/issues/33); the build
  specification is [`docs/spec/build.md`](../spec/build.md)

## Context

v1 needs screens — a preset list, a preset editor, a running workout, a summary, a settings surface
and a first-run explanation — and this repository has none. There is no user-interface framework in
the build at all, and that absence is deliberate rather than accidental:
[`docs/spec/build.md`](../spec/build.md) states it as an invariant.

> **Invariant — the skeleton stays a skeleton.** No user-interface framework, no annotation
> processing, no dependency-injection container, no test dependency that needs a device or a
> simulated Android runtime. Anything added here has to be justified by a feature that exists.

That invariant is the context for this decision rather than an obstacle to it. It was written to
stop a framework arriving speculatively, ahead of anything that needed one, and it says so in its
own last sentence: anything added has to be *justified by a feature that exists*. **v1's screens are
that feature.** The invariant therefore yields here on its own terms; it is not being broken, and it
is not being weakened for anything else it forbids.

The question left is which framework, and it is one-way in practice. A screen written twice is a
screen written twice, and every later surface — six of them as the map now stands — is written in
whichever toolkit the first one chose.

## Decision

**The user interface is Jetpack Compose with Material 3, in a single activity, with Compose
Navigation between screens.**

Why Compose: it is the current Android default, which means it is what the documentation, the
samples and the reference material the agents building this app will read are written in. That last
point is not a fashion argument. Most of the code here is written by agents against primary sources,
and a toolkit whose primary sources are the ones Google currently writes is materially cheaper to
build against than one whose best material is a decade of accumulated blog posts.

Material 3 comes with it as the theming system: a role-based colour scheme, typography and shapes
that components read by default, which is the substrate the design-system work on
[#40](https://github.com/derekwinters/Interval-trainer-android/issues/40) builds the app's
vocabulary on.

A **single activity** with Compose Navigation is the shape. The app has one entry point, and the
running-screen navigation lock decided on
[#31](https://github.com/derekwinters/Interval-trainer-android/issues/31) — the running screen is
the only reachable screen while a workout runs, and the rest of the app opens up while it is paused
— is a statement about a navigation graph. It is expressible in one; across multiple activities it
becomes a statement about task and back-stack behaviour, which is a harder thing to say and a much
harder thing to test.

## Alternatives considered

**XML layouts and Views.** The older toolkit, still supported, still shipping. Rejected: it is no
longer where Google's material goes, so the reference material an agent would work from is
increasingly historical; and the testing story this repository actually wants is the Compose one.
The consistency research on [#38](https://github.com/derekwinters/Interval-trainer-android/issues/38)
found the only genuine cross-screen invariants that are assertable on the JVM without screenshots
are assertions over the **Compose semantics tree** — every clickable node's touch target, every
node's label, a counter's position in the root. Those assertions exist because Compose exposes a
semantics tree; the View toolkit offers no equivalent that runs on a JVM-only continuous-integration
setup.

**Compose without a navigation library** — screen state held in a `when` over a sealed hierarchy,
hand-rolled. It is a real option for an app with three screens, and it avoids a dependency. Rejected
because the app has six surfaces, not three, and because system back has to be handled correctly on
every one of them: back while running raises the stop confirmation, back from a summary returns to
the preset list, and opening the app while a workout exists lands on the running screen. A
hand-rolled stack gets all of that wrong slowly, one edge case at a time.

## Consequences

- **`:app` gains the Compose dependencies and the Compose compiler.** The Compose compiler plugin,
  the Compose BOM, Material 3, the activity and navigation artefacts, and the test artefacts the
  semantics assertions need. All of them pinned as literals, per the build specification's second
  invariant, which is untouched by this decision.
- **`docs/spec/build.md`'s third invariant needs amending, and this ADR does not amend it.** The
  invariant as written forbids a user-interface framework outright; what it should say is that one
  arrives with the feature that justifies it, which is the amendment this decision earns. That is
  specification content and lands with the v1 specification on
  [#33](https://github.com/derekwinters/Interval-trainer-android/issues/33). Recording it here so it
  is not lost: **the specification is what has to change, and until it does the build specification
  and the build disagree.**
- **Compose is what makes JVM user-interface testing available at all.** The semantics-tree
  assertions described in [`docs/research/ui-consistency.md`](../research/ui-consistency.md) —
  `assertTouchHeightIsEqualTo`, `assertHeightIsAtLeast`, `assertPositionInRootIsEqualTo`,
  content-description assertions — are `androidx.compose.ui.test`, and they are the mechanism by
  which "every button of a given action looks and behaves the same on every screen" becomes a test
  rather than a note. Choosing Compose is what buys the option; whether it is taken up, and at what
  cost, is [#40](https://github.com/derekwinters/Interval-trainer-android/issues/40) and the
  specification's business.
- **Those tests need a simulated Android runtime, and that question is open.** Compose semantics
  tests on the JVM run under Robolectric, which the same research records as fetching an
  `android-all` jar from Maven Central at test time unless it is pre-fetched or vendored — a network
  dependency inside `./gradlew test`, and a live question against the build specification's
  clean-checkout invariant. It is named in
  [ADR 0005](0005-a-pure-jvm-core-and-a-thin-android-shell.md) and settled by the specification, not
  here.
- **The design system has a substrate but not yet a vocabulary.** Material 3 supplies roles;
  deciding which role each of this app's actions takes, and what the closed layout set is, is
  [#40](https://github.com/derekwinters/Interval-trainer-android/issues/40). Nothing in this ADR
  decides how anything looks.
