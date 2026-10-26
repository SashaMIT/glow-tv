#!/bin/bash
# Intelligence key response: opens a section and presses a key N times, then reads the frame times.
# Usage: tools/intelbench.sh <label> <section number> <key> [presses]
#   e.g. tools/intelbench.sh map 1 DPAD_RIGHT 20    (sections: 0 Overview, 1 World map, 2 Sports now, 3 Markets…)
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box, e.g. GLOW_DEVICE=192.168.1.50:5555}; N=${4:-20}
a() { adb -s "$D" shell "$@" 2>/dev/null | tr -d '\r'; }
a su 0 am start -n com.tv98.glow/.IntelActivity --ei section "$2" >/dev/null; sleep 8
a input keyevent KEYCODE_DPAD_RIGHT; sleep 1   # into the section (the map takes it as "next")
for pass in 1 2 3; do
  a dumpsys gfxinfo com.tv98.glow reset >/dev/null
  for i in $(seq 1 "$N"); do a input keyevent "KEYCODE_$3"; sleep 0.3; done
  sleep 1
  g=$(a dumpsys gfxinfo com.tv98.glow)
  f=$(echo "$g" | awk '/Total frames rendered/{print $4}'); j=$(echo "$g" | awk '/Janky frames:/{print $3}')
  p90=$(echo "$g" | awk '/90th percentile/{print $3}'); p99=$(echo "$g" | awk '/99th percentile/{print $3}')
  echo "$1 pass $pass: $N × $3, frames $f, janky $j, p90 $p90, p99 $p99"
done
a input keyevent KEYCODE_HOME
