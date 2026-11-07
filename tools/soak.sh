#!/bin/bash
# Leaves the box running like a household would for hours: Home, a channel, the guide, Intelligence, round and round.
# Every 10 minutes: Glow's memory, temperature, and whether Glow or the box restarted. Ends with a summary.
# Usage: tools/soak.sh OUT.csv [HOURS]   (GLOW_DEVICE set; run in the background, it takes the whole time)
set -uo pipefail
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box}; OUT=${1:?output file}; HOURS=${2:-24}
a() { adb -s "$D" shell "$@" 2>/dev/null | tr -d '\r'; }
key() { a input keyevent "$1" >/dev/null; }
go() { a su 0 am start -n "com.tv98.glow/$1" "${@:2}" >/dev/null; }
glow_pid() { a pidof com.tv98.glow; }
boot() { a cat /proc/stat | awk '/^btime/{print $2}'; }
crashes() { a su 0 cat /data/data/com.tv98.glow/files/crashes.txt | grep -cE '^[0-9]{9,} '; }
BOOT0=$(boot); PID0=$(glow_pid); CRASH0=$(crashes); END=$((SECONDS + HOURS * 3600)); NEXT=0
GLOW_RESTARTS=0; BOX_RESTARTS=0; STEP=0
echo "elapsed_min,scene,glow_mb,java_mb,native_mb,temp_c,glow_restarts,box_restarts,crashes,failed" > "$OUT"
sample() {
  local scene=$1 m tot java native temp pid b fails
  key KEYCODE_WAKEUP
  pid=$(glow_pid); b=$(boot)
  [ -n "$pid" ] && [ "$pid" != "$PID0" ] && { GLOW_RESTARTS=$((GLOW_RESTARTS + 1)); PID0=$pid; }
  [ -n "$b" ] && [ "$b" != "$BOOT0" ] && { BOX_RESTARTS=$((BOX_RESTARTS + 1)); BOOT0=$b; }
  m=$(a dumpsys meminfo com.tv98.glow)
  tot=$(echo "$m" | awk '/TOTAL:/{printf "%.1f", $2/1024; exit}')
  java=$(echo "$m" | awk '/Java Heap:/{printf "%.1f", $3/1024; exit}')
  native=$(echo "$m" | awk '/Native Heap:/{printf "%.1f", $3/1024; exit}')
  temp=$(( $(a cat /sys/class/thermal/thermal_zone0/temp || echo 0) / 1000 ))
  fails=$(a su 0 cat /data/data/com.tv98.glow/files/crashes.txt | grep -cE '^[0-9]{9,} ')
  echo "$((SECONDS / 60)),$scene,$tot,$java,$native,$temp,$GLOW_RESTARTS,$BOX_RESTARTS,$((fails - CRASH0)),$(a dumpsys window | grep -c 'Application Not Responding')" >> "$OUT"
}
wait_scene() { # runs a scene for N seconds, sampling every 10 minutes
  local until=$((SECONDS + $2))
  while [ $SECONDS -lt $until ] && [ $SECONDS -lt $END ]; do
    [ $SECONDS -ge $NEXT ] && { sample "$1"; NEXT=$((SECONDS + 600)); }
    key KEYCODE_WAKEUP; sleep 50
  done
}
while [ $SECONDS -lt $END ]; do
  adb connect "$D" >/dev/null 2>&1
  case $((STEP % 4)) in
    0) key KEYCODE_HOME; wait_scene home 1800 ;;
    1) go .ClassicGuideActivity; sleep 5; for c in 4 4 5; do key "KEYCODE_$c"; done; sleep 4; key KEYCODE_DPAD_CENTER; wait_scene watching 3000 ;;
    2) go .ClassicGuideActivity; sleep 5; for i in 1 2 3 4 5 6 7 8 9 10; do key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_RIGHT; done; wait_scene guide 300 ;;
    3) for s in 0 1 2 3 5 6 7; do go .IntelActivity --ei section "$s"; sleep 25; done; wait_scene intelligence 300 ;;
  esac
  STEP=$((STEP + 1))
done
key KEYCODE_HOME
python3 - "$OUT" <<'EOF'
import csv, sys
rows = list(csv.DictReader(open(sys.argv[1])))
last = rows[-1]
mb = [float(r["glow_mb"]) for r in rows if r["glow_mb"]]
home = [float(r["glow_mb"]) for r in rows if r["glow_mb"] and r["scene"] == "home"]
temp = [int(r["temp_c"]) for r in rows]
print(f"{float(last['elapsed_min']) / 60:.1f} hours, {len(rows)} samples")
print(f"Glow restarts {last['glow_restarts']}, box restarts {last['box_restarts']}, crashes {last['crashes']}")
print(f"memory {min(mb):.0f}-{max(mb):.0f} MB; on Home first {home[0] if home else 0:.0f}, last {home[-1] if home else 0:.0f} MB")
print(f"temperature {min(temp)}-{max(temp)} °C")
EOF
