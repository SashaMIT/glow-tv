#!/bin/bash
# Browsing smoothness: the TV guide grid vs the Free TV channel list, same key path, gfxinfo per pass.
# Usage: tools/guidebench.sh guide|list [passes]
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box, e.g. GLOW_DEVICE=192.168.1.50:5555}; N=${2:-3}
a() { adb -s "$D" shell "$@" 2>/dev/null | tr -d '\r'; }
if [ "$1" = guide ]; then
  a su 0 am start -n com.tv98.glow/.ClassicGuideActivity >/dev/null; sleep 3; a input keyevent KEYCODE_1; sleep 3
else
  a su 0 am start -n com.tv98.glow/.ChannelsActivity --es source free >/dev/null; sleep 4; a input keyevent KEYCODE_DPAD_DOWN; sleep 2
fi
tot=0; frames=0; p99s=""; p90s=""; passes=""
for r in $(seq 1 "$N"); do
  a dumpsys gfxinfo com.tv98.glow reset >/dev/null
  for i in $(seq 1 20); do a input keyevent KEYCODE_DPAD_DOWN; sleep 0.2; done
  if [ "$1" = guide ]; then for i in $(seq 1 6); do a input keyevent KEYCODE_DPAD_RIGHT; sleep 0.2; done; for i in $(seq 1 6); do a input keyevent KEYCODE_DPAD_LEFT; sleep 0.2; done; fi
  for i in $(seq 1 20); do a input keyevent KEYCODE_DPAD_UP; sleep 0.2; done
  sleep 1
  g=$(a dumpsys gfxinfo com.tv98.glow)
  f=$(echo "$g" | awk '/Total frames rendered/{print $4}'); j=$(echo "$g" | awk '/Janky frames:/{print $3}')
  p=$(echo "$g" | awk '/99th percentile/{print $3}'); q=$(echo "$g" | awk '/90th percentile/{print $3}')
  frames=$((frames+f)); tot=$((tot+j)); p99s="$p99s $p"; p90s="$p90s $q"; passes="$passes $(python3 -c "print(round(100*$j/max($f,1),1))")%"
done
echo "$1: janky $tot/$frames frames = $(python3 -c "print(round(100*$tot/max($frames,1),2))")% (per pass:$passes), p90 per pass:$p90s, p99 per pass:$p99s"
