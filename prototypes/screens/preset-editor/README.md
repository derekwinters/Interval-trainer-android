# Preset editor — design exploration

This directory holds the design exploration for the interval timer's **preset editor** — the
last of the three screens issue #29 asks for, and the one where the ticket's own description
turned out to be wrong.

## A correction to the ticket

Issue #29 described this screen as five fixed fields: name, warm-up, work, rest, rounds,
cool-down. That's the fixed-rounds template the project's map (#22) had already moved away
from before this ticket was even resolved, specifically because a real workout — uneven round
durations — can't be expressed as one work duration times one recovery duration times a round
count. The actual editor has to be a list of independently-authored rows.

## The settled design

`EditorList.dc.html` (rendered at `previews/EditorList.png`) is the settled design: each
interval in the preset is its own row, with a drag handle to reorder it, a tappable duration to
edit it in place, and a delete control. It's built against a genuinely non-uniform schedule to
prove the point — the list isn't locked into any repeating structure.

A "+ Rounds…" action opens the generator, `EditorScroll.dc.html`, to bulk-insert a uniform
block of rows when that's a useful starting point. It bulk-inserts into the list; it is not a
second editor, and nothing about "N rounds" is remembered afterward — every row it inserts
becomes an ordinary row, exactly like one added by hand.

## The settled duration input

`DurScroll.dc.html` (rendered at `previews/DurScroll.png`) is the settled duration-input
control, used by `EditorList.dc.html` whenever a row's duration is tapped: a scroll picker — a
flick-scrub drum for minutes and seconds — chosen over steppers, typed digits, and quick-select
chips. It reuses the fade/shrink treatment already used by the running screen's schedule rail,
rather than inventing a new one.

## The archived exploration

- `EditorSteppers.dc.html` / `DurSteppers.dc.html` — the stepper alternative, both the
  duration input and the generator quick-form built with it. Lost to the scroll picker.
- `DurTyped.dc.html` — typed numeric entry. Ruled out.
- `DurChips.dc.html` — quick-select common durations plus fine adjust. Ruled out.

## About the file formats

The `.dc.html` files are Claude Design Component sources. They render correctly as plain
static HTML — open any one directly in a browser — but they were authored for Claude's
design-canvas tooling, which is needed to reopen them as an editable canvas.

The PNGs under `previews/` are the practical, tooling-independent reference. That's what to
look at to see the designs.

## Status

This is a checkpoint committed to `main` as reference material, resolving the preset-editor
part of #29 alongside the home screen in `prototypes/screens/home-screen/`.
