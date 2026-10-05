#!/usr/bin/env bash
# Screenshot spike (throwaway): prints each PNG given as base64 between marker lines, so the job
# log can carry the bytes out of the runner.
set -euo pipefail
for f in "$@"; do
  name=$(basename "$f")
  echo "=====SPIKE-PNG-META $name sha256=$(sha256sum "$f" | cut -d' ' -f1) bytes=$(wc -c < "$f")"
  echo "=====SPIKE-PNG-BEGIN $name"
  base64 -w 76 "$f"
  echo "=====SPIKE-PNG-END $name"
done
