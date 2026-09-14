# Home screen — design exploration

This directory holds the design exploration for the interval timer's **home screen** — the
preset list, one of three screens issue #29 asks for.

## The settled design

`HomeA.dc.html` (rendered at `previews/HomeA.png`) is the settled design: two buttons, Edit
and Start, always visible side by side on every row. Nothing else on the row is tappable, so a
tap can never start a workout by accident — the row's name and summary are inert text, and only
those two explicit controls act. Each row also carries a thin colour strip previewing the
preset's shape (its work/recovery/warm-up/cool-down proportions), reusing the running screen's
palette.

## The archived exploration

The other three files are earlier directions for the same problem — keeping a tap from
starting a workout by accident — each trying a different safety mechanism instead of the
two-always-visible-buttons approach:

- `HomeB.dc.html` — tap a row to reveal Start and Edit underneath, rather than acting
  immediately.
- `HomeC.dc.html` — a small, quiet edit icon on the row's leading edge and a bold Start pill
  on its trailing edge, with no tap-anywhere zone in between.
- `HomeD.dc.html` — tapping a row only selects it; a persistent bottom bar carries Start and
  Edit for whichever preset is currently selected.

## About the file formats

The `.dc.html` files are Claude Design Component sources. They render correctly as plain
static HTML — open any one directly in a browser — but they were authored for Claude's
design-canvas tooling, which is needed to reopen them as an editable canvas.

The PNGs under `previews/` are the practical, tooling-independent reference. That's what to
look at to see the designs.

## Status

This is a checkpoint committed to `main` as reference material, resolving the home-screen part
of #29 alongside the preset editor in `prototypes/screens/preset-editor/`.
