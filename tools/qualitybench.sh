#!/bin/bash
# Picture quality: plays the first N channels of a guide section for S seconds each and prints, per channel,
# the best variant offered, what played first, and what was playing at the end. Usage: tools/qualitybench.sh [section-key] [N] [S]
D=${GLOW_DEVICE:?set GLOW_DEVICE}; KEY=${1:-6}; N=${2:-8}; S=${3:-25}
a() { adb -s "$D" shell "$@" >/dev/null 2>&1; }
k() { a su 0 input keyevent "$@"; }
k KEYCODE_HOME; sleep 4
a su 0 am start -n com.tv98.glow/.ClassicGuideActivity; sleep 3; k KEYCODE_$KEY; sleep 3
adb -s "$D" logcat -c
k DPAD_CENTER; sleep "$S"
for i in $(seq 2 "$N"); do k DPAD_UP; sleep "$S"; done
k BACK; k BACK; k BACK
adb -s "$D" logcat -d -s GlowPlayer | sed 's/^.*GlowPlayer: //' | grep -E "offers|playing [0-9]" | awk -F': ' '
  { ch=$1; msg=$2 }
  msg ~ /^offers/ { offers[ch]=msg; if (!(ch in order)) { order[ch]=++n; names[n]=ch } }
  msg ~ /^playing/ { if (!(ch in first)) first[ch]=msg; last[ch]=msg; if (!(ch in order)) { order[ch]=++n; names[n]=ch } }
  END { for (i=1;i<=n;i++) { c=names[i]; printf "%-28s %-32s first: %-38s end: %s\n", c, offers[c], first[c], last[c] } }'
