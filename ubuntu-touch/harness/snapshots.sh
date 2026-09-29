#!/usr/bin/env bash
# Runs the real Qt app (built with -DNOOZ_TEST_HOOKS=ON) on a virtual X display (run under xvfb-run) against tools/native/testserver.py at
# several window sizes and writes a PNG of each. Fails if the app does not produce every picture.
#   snapshots.sh <path-to-nooz-binary> <out-dir>
set -euo pipefail
BIN=$(readlink -f "$1"); OUT=$(mkdir -p "$2" && readlink -f "$2")
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
python3 "$ROOT/tools/native/testserver.py" > "$OUT/port.txt" &
SERVER=$!
trap 'kill $SERVER 2>/dev/null || true' EXIT
for _ in $(seq 50); do [ -s "$OUT/port.txt" ] && break; sleep 0.1; done
PORT=$(head -1 "$OUT/port.txt")

shoot() { # name WxH expected-two-pane(0|1) [open-first]
  local name=$1 size=$2 want=$3 open=${4:-}
  local log; log=$(mktemp)
  local home; home=$(mktemp -d)
  env HOME="$home" XDG_DATA_HOME="$home/data" XDG_CACHE_HOME="$home/cache" \
      QT_QPA_PLATFORM=xcb QT_QUICK_BACKEND=software \
      NOOZ_FONTS_DIR="$ROOT/core/design/src/commonMain/composeResources/font" \
      NOOZ_BOOT_FEED="http://127.0.0.1:$PORT/feed.xml" NOOZ_SIZE="$size" \
      NOOZ_SNAPSHOT="$OUT/$name.png" NOOZ_SNAPSHOT_DELAY_MS="${SNAP_DELAY:-5000}" ${open:+NOOZ_OPEN_FIRST=1} \
      "$BIN" 2>"$log" || true
  grep -q "LAYOUT twoPane=$want" "$log" || { echo "FAIL $name: expected twoPane=$want"; grep LAYOUT "$log" || cat "$log"; exit 1; }
  test -s "$OUT/$name.png" || { echo "no snapshot for $name" >&2; exit 1; }
  echo "wrote $name.png"
}

shoot phone-list 400x760 0
shoot phone-reader 400x760 0 open
shoot small-700x700 700x700 0
shoot below-839x800 839x800 0
shoot at-840x800 840x800 1
shoot tablet-1024x768 1024x768 1
shoot desktop-1280x800 1280x800 1
shoot ultrawide-1900x900 1900x900 1
