#!/bin/bash
# Zap benchmark: opens Free TV > World news, plays the first channel, presses ▲ N times, reports start times.
# Usage: tools/zapbench.sh <label> [zaps]
set -u
cd "$(dirname "$0")/.."
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box, e.g. GLOW_DEVICE=192.168.1.50:5555}; N=${2:-20}; mkdir -p bench; OUT="bench/zap-$1.txt"
a() { adb -s "$D" shell "$@" 2>/dev/null | tr -d '\r'; }
sel() { a 'uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml' | tr '>' '\n' | grep -oE 'text="[^"]*·[^"]*working[^"]*"' | head -1; }
a input keyevent KEYCODE_HOME; sleep 4; a input keyevent KEYCODE_DPAD_DOWN; sleep 0.6; a input keyevent KEYCODE_DPAD_CENTER; sleep 5
for i in 1 2 3 4 5 6; do sel | grep -q "World news" && break; a input keyevent KEYCODE_DPAD_DOWN; sleep 1.3; done
a input keyevent KEYCODE_DPAD_RIGHT; sleep 0.8; a logcat -c
a input keyevent KEYCODE_DPAD_CENTER; sleep 10
for i in $(seq 1 "$N"); do a input keyevent KEYCODE_DPAD_UP; sleep 9; done
a logcat -d -s GlowPlayer:I | grep -E "PLAYING|FAILED|handing" | sed 's/.*GlowPlayer: //' > "$OUT"
a input keyevent KEYCODE_BACK; sleep 1; a input keyevent KEYCODE_HOME
python3 - "$OUT" <<'PY'
import re, sys, statistics
lines = open(sys.argv[1]).read().splitlines()
t = [int(m.group(1)) for l in lines for m in [re.search(r'PLAYING .* after (\d+) ms', l)] if m]
f = sum('FAILED' in l for l in lines); y = sum('handing' in l for l in lines)
print(f"{sys.argv[1]}: {len(t)} played, {f} failed, {y} to YouTube; start time median {statistics.median(t):.0f} ms, "
      f"p75 {sorted(t)[int(len(t)*.75)-1] if t else 0} ms, min {min(t) if t else 0}, max {max(t) if t else 0}")
PY
