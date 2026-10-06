#!/usr/bin/env bash
# Propose changed screenshots as one pull request (docs/spec/build.md BUILD-087).
#
# Run from a checkout of main, with GH_TOKEN set and the checkout's credentials able to push.
# The only push is to the screenshots branch; main and every other pull request are never touched.
#
# Usage: propose_screenshots.sh <dir_with_the_six_pngs>
set -euo pipefail

captured="$1"
branch="screenshots/update"
dest="docs/screenshots"
title="docs: update screenshots"

mkdir -p "$dest"
cp "$captured"/*.png "$dest"/

if [ -z "$(git status --porcelain -- "$dest")" ]; then
  echo "The captured screenshots match docs/screenshots/; nothing to propose."
  exit 0
fi

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git checkout -B "$branch"
git add -- "$dest"
git commit -m "$title"
git push --force origin "HEAD:refs/heads/$branch"

body_file="$(mktemp)"
cat > "$body_file" <<'BODY'
The screenshot workflow captured the app's screens on an emulator, and some differ from the copies committed under `docs/screenshots/`. This pull request replaces only the images that changed, so the README shows the app as it is now.

## Deviations and Decisions

None.

**Docs:** the changed PNGs under `docs/screenshots/` are the documentation change. They are produced by `.github/workflows/screenshots.yml` (`docs/spec/build.md` §11). This pull request closes no issue, so it carries the `no-closing-keyword` label.
BODY

existing="$(gh pr list --head "$branch" --base main --state open --json number --jq '.[0].number // empty')"
if [ -z "$existing" ]; then
  gh pr create --base main --head "$branch" --title "$title" --body-file "$body_file" \
    --label no-closing-keyword
else
  gh pr edit "$existing" --add-label no-closing-keyword
  echo "Updated the open pull request #$existing."
fi
