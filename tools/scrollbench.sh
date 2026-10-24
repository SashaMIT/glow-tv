#!/bin/bash
# Home-screen scroll smoothness: N passes of the same remote-key path, gfxinfo after each. Usage: tools/scrollbench.sh <label> [passes]
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box, e.g. GLOW_DEVICE=192.168.1.50:5555}; N=${2:-3}
a() { adb -s "$D" shell "$@" 2>/dev/null | tr -d '\r'; }
a input keyevent KEYCODE_HOME; sleep 5
tot=0; slow=0; frames=0; p99s=""
for r in $(seq 1 "$N"); do
  a dumpsys gfxinfo com.tv98.glow reset >/dev/null
  for i in $(seq 1 10); do a input keyevent KEYCODE_DPAD_RIGHT; sleep 0.25; done
  a input keyevent KEYCODE_DPAD_DOWN; sleep 0.4
  for i in $(seq 1 8); do a input keyevent KEYCODE_DPAD_RIGHT; sleep 0.25; done
  for i in $(seq 1 18); do a input keyevent KEYCODE_DPAD_LEFT; sleep 0.2; done
  a input keyevent KEYCODE_DPAD_UP; sleep 1
  g=$(a dumpsys gfxinfo com.tv98.glow)
  f=$(echo "$g" | awk '/Total frames rendered/{print $4}'); j=$(echo "$g" | awk '/Janky frames:/{print $3}')
  s=$(echo "$g" | awk '/Slow issue draw commands/{print $6}'); p=$(echo "$g" | awk '/99th percentile/{print $3}')
  frames=$((frames+f)); tot=$((tot+j)); slow=$((slow+s)); p99s="$p99s $p"
done
echo "$1: janky $tot/$frames frames = $(python3 -c "print(round(100*$tot/$frames,2))")%, slow draw commands $slow, p99 per pass:$p99s"
