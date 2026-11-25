#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "$0")"
log=$(mktemp)
trap 'rm -f -- "$log"' EXIT

mvn -B -Dstyle.color=never test | tee "$log"

printf '\nScenario\tCamunda end event\tSupplier state\tCompleteJob\tCancellations\n'
LC_ALL=C awk '/^RESULT / { print substr($0, 8) }' "$log" | LC_ALL=C sort |
  diff -u expected.tsv -
cat expected.tsv
