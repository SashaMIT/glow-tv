#!/bin/bash
# Browsing smoothness, from a fresh start of the screen each time (an already-open guide may be sitting on its menu).
# Usage: tools/guidebench.sh guide [passes]   — All Channels: 20 ▼ + 20 ▲, then 8 ▶ + 8 ◀, reported separately
#        tools/guidebench.sh list [passes]    — the Free TV channel list, 20 ▼ + 20 ▲
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box, e.g. GLOW_DEVICE=192.168.1.50:5555}; N=${2:-3}
a() { adb -s "$D" shell "$@" 2>/dev/null | tr -d '\r'; }
g() { a dumpsys gfxinfo com.tv98.glow | awk '/Total frames rendered/{f=$4} /Janky frames:/{j=$3} /90th percentile/{p=$3} /99th percentile/{q=$3}
      END{printf "%d/%d late (%.1f%%), p90 %s p99 %s", j, f, 100*j/(f?f:1), p, q}'; }
t=$(a cat /sys/class/thermal/thermal_zone0/temp)
echo "box at $((t / 1000)) °C$([ "$t" -gt 70000 ] && echo ' (warm: results run slower; let it cool first)')"
a su 0 am force-stop com.tv98.glow; sleep 2; a input keyevent KEYCODE_HOME; sleep 15
if [ "$1" = guide ]; then
  a su 0 am start -n com.tv98.glow/.ClassicGuideActivity >/dev/null; sleep 6; a input keyevent KEYCODE_1; sleep 5
else
  a su 0 am start -n com.tv98.glow/.ChannelsActivity --es source free >/dev/null; sleep 5; a input keyevent KEYCODE_DPAD_DOWN; sleep 2
fi
for r in $(seq 1 "$N"); do
  a dumpsys gfxinfo com.tv98.glow reset >/dev/null
  for i in $(seq 1 20); do a input keyevent KEYCODE_DPAD_DOWN; sleep 0.2; done
  for i in $(seq 1 20); do a input keyevent KEYCODE_DPAD_UP; sleep 0.2; done
  sleep 1; line="$1 pass $r: ▲▼ $(g)"
  if [ "$1" = guide ]; then
    a dumpsys gfxinfo com.tv98.glow reset >/dev/null
    for i in $(seq 1 8); do a input keyevent KEYCODE_DPAD_RIGHT; sleep 0.2; done
    for i in $(seq 1 8); do a input keyevent KEYCODE_DPAD_LEFT; sleep 0.2; done
    sleep 1; line="$line   ·   ◀▶ $(g)"
  fi
  echo "$line"
done
