# Running screen — design exploration

This directory holds the design exploration for the interval timer's **running-workout
screen** — the screen shown while a workout is actually counting down. It is one of three
screens issue #29 asks for; the other two, the preset editor and the home screen, have not
been designed yet.

## The settled design

`Main.dc.html` (rendered at `previews/Main.png`) is the settled design: a progress ring
showing the current interval's name, colour, and remaining time, with a narrow vertical rail
beside it listing the full schedule. Past and future intervals in that rail shrink and dim
with distance from the current one, strung along a thin connecting spindle.

Two decisions the owner made explicitly are reflected in it:

- Start, Pause, and Resume are merged into one toggle control, not three separate buttons.
- What's next is carried by the rail itself, not a separate line of text, so it reads as
  clearly secondary to the current activity.

## The archived exploration

The other eight files are not dead ends — they're the rounds of exploration that led to the
settled design, kept for reference. Each caption below is reused from `canvas.json`'s
`annotations` array, so the two stay in agreement.

Three other treatments of the schedule rail, once the ring + rail combination was chosen:

- `RolodexPerspective.dc.html` — **Tilted wheel.** Rows curve away like a physical Rolodex
  drum. The most literal reading of "rolodex"; not picked.
- `RolodexTicks.dc.html` — **Proportional map.** A spatial map of the whole workout, tick
  length matching real duration, with a playhead at now; not picked.
- `RolodexFaded.dc.html` — **Elevated cards, edge fade.** The card idea again, more polished:
  shadows, round-number badges, edge fade; not picked.

Five earlier full-screen layout directions, considered before the ring + rail combination:

- `RingOnly.dc.html` — **Ring timer (no rail).** The original pick, before the schedule rail
  was added.
- `FullBleed.dc.html` — **Full-bleed colour.** The interval colour floods the whole screen —
  readable at a glance, even through a pocket check or bright sun.
- `Card.dc.html` — **Card layout.** One elevated card holds the current activity; a smaller,
  flatter card underneath previews what's next.
- `Minimal.dc.html` — **Minimal typographic.** Almost no chrome — a colour dot and type do
  the work. Next-up is one small caption line, controls are icon-only.
- `States.dc.html` — **One layout across states.** The ring layout through Get ready,
  Running, Paused and Muted, to check the idea still reads as the workout changes.

## About the file formats

The `.dc.html` files are Claude Design Component sources. They render correctly as plain
static HTML — open any one directly in a browser — but they were authored for Claude's
design-canvas tooling. `canvas.json` is that tooling's layout manifest and has no meaning
outside it.

The PNGs under `previews/` are the practical, tooling-independent reference. That's what to
look at to see the designs.

## Status

This is a checkpoint committed to `main` as reference material, ahead of #29's own
resolution. #29 still needs the preset editor and home screen designed, and then — per this
repository's wayfinder convention (see `CLAUDE.md`) — a resolution comment and a closing
document.
