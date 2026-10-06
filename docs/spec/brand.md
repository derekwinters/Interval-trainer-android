# Specification — Brand (`BRAND`)

The app's launcher icon and its horizontal logo: what they draw, in which colours, how the icon
survives a launcher's mask and Android's themed-icon mode, and which files carry them.

The design is the one chosen from the concept rounds on
[#156](https://github.com/derekwinters/Interval-trainer-android/issues/156): **a stopwatch whose
case is the split work/recovery ring** (concept "D2"). Every value on this page is fixed — nothing
about the drawing is left to whoever implements it.

---

## Invariants

> **Invariant — the launcher icon's colours are the design-system tokens, and are never redefined
> separately.** Every colour the icon paints is one of `DesignSystemColors.Default`'s tokens
> ([`docs/spec/design-system.md`](design-system.md) `DS-050`), named in `BRAND-002`. Android
> resources cannot reference a Compose `Color`, so the hex values are written into the icon's XML
> — but they are a copy of the tokens, never a second palette: a change to a token that the icon
> does not follow fails `./gradlew test` (`BRAND-003`), so a palette change cannot silently leave
> the icon behind.

> **Invariant — the master SVG is the source of truth.** `docs/brand/icon.svg` (`BRAND-017`) is
> the drawing; the foreground, the monochrome layer, both VectorDrawables and the logo's tile are
> derived from it and never drawn independently. A change to the icon is a change to the master
> first.

---

## 1. Canvas and colours

- **BRAND-001** All coordinates are in the Android adaptive-icon canvas: **108 × 108 units, 1 unit
  = 1dp**. Angles are measured **clockwise from 12 o'clock**. The stopwatch is centred at
  **(54, 58)**, 4 units below the canvas centre, so that the crown sits inside the safe zone
  (`BRAND-020`). *(manual: read against the master SVG.)*
- **BRAND-002** The icon and logo use exactly these colours, each one an existing
  `DesignSystemColors.Default` token; no new colour is introduced:

  | Role | Token | Hex |
  |---|---|---|
  | Background | `bg` | `#121212` |
  | Work arc | `work` | `#2ECC71` |
  | Recovery arc | `recovery` | `#F5C518` |
  | Crown, hand, centre dot, logo wordmark on dark | `fg` | `#F5F5F5` |
  | Logo wordmark on light | `bg` | `#121212` |

  *(auto: `LauncherIconColourTokenTest.kt`, for the Android resources; the logo SVGs are manual.)*
- **BRAND-003** Every colour in `app/src/main/res/values/ic_launcher_background.xml` and
  `app/src/main/res/drawable/ic_launcher_foreground.xml` equals the `DesignSystemColors.Default`
  token `BRAND-002` assigns to its role, compared as values, so that a token change the icon does
  not follow turns `./gradlew test` red. This is the first invariant's check. *(auto:
  `LauncherIconColourTokenTest.kt`.)*

## 2. The drawing

The foreground layer is exactly these six elements, in this order:

- **BRAND-010** **Work arc:** a circle centred (54, 58), radius 23, from 22° to 250° clockwise (a
  228° sweep). Stroke `work`, width 7, round caps, no fill. Endpoints: start (62.62, 36.67), end
  (32.39, 65.87); large-arc flag 1, sweep flag 1. *(manual.)*
- **BRAND-011** **Recovery arc:** the same circle, from 270° to 338° clockwise (a 68° sweep).
  Stroke `recovery`, width 7, round caps, no fill. Endpoints: start (31.00, 58.00), end
  (45.38, 36.67); large-arc flag 0, sweep flag 1. *(manual.)*
- **BRAND-012** **Crown cap:** a rectangle at x 49, y 22, width 10, height 6, corner radius 2.
  Fill `fg`. *(manual.)*
- **BRAND-013** **Crown stem:** a rectangle at x 52, y 27, width 4, height 6, square corners. Fill
  `fg`. *(manual.)*
- **BRAND-014** **Hand:** a line from (54, 58) to (66.12, 65.00) — length 14 at 120°, 4 o'clock.
  Stroke `fg`, width 4, round caps. *(manual.)*
- **BRAND-015** **Centre dot:** a circle centred (54, 58), radius 3.5. Fill `fg`. *(manual.)*
- **BRAND-016** The two gaps between the arcs stay open and are never closed: 32° at the top (338°
  to 22°), which makes room for the crown, and 20° from 250° to 270°, which separates work from
  recovery. Both are measured between arc endpoints, before the round caps are added. *(manual.)*
- **BRAND-017** `docs/brand/icon.svg` is the master, exactly as written here, element for element
  and value for value — the background is `bg` filling the whole canvas:

  ```svg
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">
    <rect width="108" height="108" fill="#121212"/>
    <path d="M62.62 36.67 A23 23 0 1 1 32.39 65.87" fill="none" stroke="#2ECC71" stroke-width="7" stroke-linecap="round"/>
    <path d="M31.00 58.00 A23 23 0 0 1 45.38 36.67" fill="none" stroke="#F5C518" stroke-width="7" stroke-linecap="round"/>
    <rect x="49" y="22" width="10" height="6" rx="2" fill="#F5F5F5"/>
    <rect x="52" y="27" width="4" height="6" fill="#F5F5F5"/>
    <path d="M54 58 L66.12 65" fill="none" stroke="#F5F5F5" stroke-width="4" stroke-linecap="round"/>
    <circle cx="54" cy="58" r="3.5" fill="#F5F5F5"/>
  </svg>
  ```

  *(manual.)*

## 3. Safe zone

- **BRAND-020** Nothing in the foreground extends more than 33 units from the canvas centre
  (54, 54), so no launcher mask clips the drawing. A launcher can show at most the centred 72 × 72
  square (18 to 90 on each axis) and is guaranteed to show only the centred circle of diameter 66.
  As drawn, the arcs' outer edge (radius 26.5 around (54, 58)) reaches at most 30.5 from (54, 54),
  and the crown cap's top corners, (49, 22) and (59, 22), are 32.4 from it. *(manual: the
  arithmetic here, and on a device — `BRAND-060`.)*

## 4. Monochrome (themed icon)

- **BRAND-030** The monochrome layer, which Android 13 and later use for themed icons, is the same
  six elements in the same geometry, every one painted `#FFFFFF`, on a transparent background.
  Android reads only its alpha. Both gaps of `BRAND-016` stay open in it, so the work/recovery
  split still reads in one colour. *(manual.)*

## 5. The logo

The logo is a horizontal lockup: the icon tile, then the wordmark "Interval Trainer".

- **BRAND-040** **Tile:** the master's visible 72 × 72 window (canvas x and y 18 to 90), clipped to
  a rounded square of corner radius 21.6 (30% of the side). In logo units the tile is 72 × 72 at
  the origin. *(manual.)*
- **BRAND-041** **Typeface:** [Barlow Semi Condensed](https://fonts.google.com/specimen/Barlow+Semi+Condensed)
  (SIL Open Font License). "Interval" is set in Medium (500) and "Trainer" in ExtraBold (800), at
  font size 41.14 units — a 28.8-unit cap height at Barlow's 0.70 cap-height ratio, 0.4 of the tile
  side. Letter-spacing is 0.01em. The two words are separated by a gap of 0.22em (9.05 units)
  instead of a space glyph. *(manual.)*
- **BRAND-042** **Placement:** the wordmark starts 19.5 units right of the tile's right edge (0.27
  of the tile side), measured to the left edge of the "I" glyph's ink. The baseline is at y 50.4,
  which centres the cap height on the tile's centre line (y 36). *(manual.)*
- **BRAND-043** The wordmark is converted to outlines in the committed SVG, so the logo renders
  without the font: neither logo SVG contains a `<text>` element, and the font file is not
  committed. *(manual.)*
- **BRAND-044** The artboard is 72 units tall, with no padding, and exactly as wide as the
  wordmark's ink extends. *(manual.)*
- **BRAND-045** There are two variants with identical geometry: `logo-dark.svg`, for dark
  backgrounds, with the wordmark in `fg` (`#F5F5F5`); and `logo-light.svg`, for light backgrounds,
  with the wordmark in `bg` (`#121212`). The tile is the same in both. *(manual.)*

## 6. Files

- **BRAND-050** The SVG sources live in `docs/brand/`:

  | File | Content |
  |---|---|
  | `docs/brand/icon.svg` | The master (`BRAND-017`) |
  | `docs/brand/icon-foreground.svg` | Elements `BRAND-010`–`015` only, transparent background, 108 × 108 |
  | `docs/brand/icon-monochrome.svg` | Elements `BRAND-010`–`015` in `#FFFFFF`, transparent, 108 × 108 (`BRAND-030`) |
  | `docs/brand/logo-dark.svg` | The lockup, `fg` wordmark (`BRAND-045`) |
  | `docs/brand/logo-light.svg` | The lockup, `bg` wordmark (`BRAND-045`) |

  *(manual.)*
- **BRAND-051** The Android resources are an adaptive icon alone — `minSdk` is 26, so it covers
  every supported version and no PNG mipmaps exist:

  | File | Content |
  |---|---|
  | `app/src/main/res/drawable/ic_launcher_foreground.xml` | VectorDrawable, 108dp × 108dp, viewport 108 × 108, the paths and paints of `icon-foreground.svg`, `strokeLineCap="round"` |
  | `app/src/main/res/drawable/ic_launcher_monochrome.xml` | The same VectorDrawable with every paint `#FFFFFFFF` |
  | `app/src/main/res/values/ic_launcher_background.xml` | `<color name="ic_launcher_background">#121212</color>` |
  | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | `<adaptive-icon>` with background `@color/ic_launcher_background`, foreground `@drawable/ic_launcher_foreground` and monochrome `@drawable/ic_launcher_monochrome` |
  | `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | Identical to `ic_launcher.xml` |

  *(manual.)*
- **BRAND-052** `AndroidManifest.xml`'s `<application>` declares
  `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`, so the
  application's icon resolves to an `AdaptiveIconDrawable` on API 26 and later, and on API 33 and
  later that drawable has a non-null monochrome layer. *(auto: `LauncherIconTest.kt`, under
  Robolectric — [`docs/spec/build.md`](build.md) `BUILD-023`.)*

## 7. On a device

- **BRAND-060** On a real launcher the icon shows with the crown fully visible inside a circular
  mask, and with themed icons turned on it shows the monochrome form. *(manual: on a device, by the
  repository owner.)*

## Out of scope

- **The notification small icon** (`ic_notification.xml`,
  [`docs/spec/service.md`](service.md) `SVC-020`) stays the existing diamond.
- Play Store listing graphics (the 512 × 512 PNG and the feature graphic) — the app is sideloaded.
- An in-app splash screen, or any other in-app use of the logo.
- Any change to the colour tokens themselves (`DS-050`).

---

## Traceability

| Section | IDs | Tests |
|---|---|---|
| Canvas and colours | BRAND-001–003 | `BRAND-002` (Android resources), `BRAND-003`: `app/src/test/java/com/derekwinters/intervaltrainer/LauncherIconColourTokenTest.kt`; `BRAND-001` *(manual)* |
| The drawing | BRAND-010–017 | *(manual)* |
| Safe zone | BRAND-020 | *(manual)* |
| Monochrome | BRAND-030 | *(manual)* |
| The logo | BRAND-040–045 | *(manual)* |
| Files | BRAND-050–052 | `BRAND-052`: `app/src/test/java/com/derekwinters/intervaltrainer/LauncherIconTest.kt`; the rest *(manual)* |
| On a device | BRAND-060 | *(manual)* |

**23 requirements, 3 `auto` and 20 `manual`.**

The drawing is a fixed set of numbers, so most of this page is checked by reading the committed
files against it rather than by a test. What a test can usefully hold is the two places drift
would be silent: a token change the icon's resources do not follow (`BRAND-003`), and an icon the
manifest does not actually resolve to an adaptive icon with a themed layer (`BRAND-052`).
