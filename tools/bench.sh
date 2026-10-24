#!/bin/bash
# Repeatable before/after benchmark for Glow on the TV98 box.
# Usage: tools/bench.sh <label> [--no-reboot]     e.g. tools/bench.sh before
# Writes bench/<label>.txt next to this script's project. Takes ~6 minutes (reboot + settle + tests).
set -u
cd "$(dirname "$0")/.."
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box, e.g. GLOW_DEVICE=192.168.1.50:5555}
LABEL=${1:-run}
mkdir -p bench
OUT="bench/$LABEL.txt"
: > "$OUT"
a() { adb -s "$D" shell "$@" 2>/dev/null | tr -d '\r'; }
key() { a input keyevent "$@"; }
log() { echo "$*" | tee -a "$OUT"; }

if [ "${2:-}" != "--no-reboot" ]; then
    adb -s "$D" reboot; sleep 25
    for i in $(seq 1 60); do
        adb connect "$D" >/dev/null 2>&1
        [ "$(a getprop sys.boot_completed)" = "1" ] && break; sleep 3
    done
    sleep 90 # let boot-time work settle
fi

log "=== $LABEL  $(date '+%Y-%m-%d %H:%M')"
log "--- system (idle, home showing)"
log "used_ram_kb=$(a dumpsys meminfo | grep 'Used RAM' | sed 's/[^0-9]*\([0-9,]*\)K.*/\1/' | tr -d ,)"
log "mem_available_kb=$(a cat /proc/meminfo | awk '/MemAvailable/{print $2}')"
log "zram=$(a cat /proc/swaps | awk 'NR>1{print "size_kb:"$3",used_kb:"$4}')"
log "loadavg=$(a cat /proc/loadavg | cut -d' ' -f1-3)"
log "cpu_temp_mC=$(a su 0 cat /sys/class/thermal/thermal_zone0/temp)"
log "awlog_cpu_time=$(a ps -A -o TIME,NAME | grep -i awlog | tr '\n' ' ')"
log "boot_list_parse=$(a logcat -d -s GlowPerf:I | grep 'lists loaded' | head -1 | sed 's/.*lists loaded: //')"
log "boot_logo_downloads=$(a logcat -d -s GlowPerf:I | grep -c 'logo from network')"
log "glow_home_pss_kb=$(a dumpsys meminfo com.tv98.glow | awk '/TOTAL:/{print $2; exit}')"
log "glow_compile=$(a dumpsys package dexopt | grep -A2 '\[com.tv98.glow\]' | grep -oE 'status=[a-z-]+' | head -1)"

log "--- cold start x5 (Glow is the home app: force-stop, press Home, read Android's 'Displayed' time)"
for i in 1 2 3 4 5; do
    a am force-stop com.tv98.glow; a logcat -c; sleep 2
    key KEYCODE_HOME; sleep 8
    t=$(a logcat -d | grep -oE 'Displayed com.tv98.glow/.HomeActivity: \+[0-9]+s?[0-9]*ms' | head -1 \
        | grep -oE '[0-9]+s?[0-9]*ms' | awk '{ if ($0 ~ /s[0-9]/) { split($0, p, "s"); sub("ms","",p[2]); print p[1]*1000+p[2] } else { sub("ms",""); print } }')
    p=$(a logcat -d -s GlowPerf:I | grep 'lists loaded' | head -1 | sed 's/.*in \([0-9]*\) ms.*/\1/')
    l=$(a logcat -d -s GlowPerf:I | grep -c 'logo from network')
    log "run$i start_ms=$t list_parse_ms=${p:-?} logo_downloads=$l"
done

log "--- scroll smoothness (home rows)"
a dumpsys gfxinfo com.tv98.glow reset >/dev/null
for i in $(seq 1 10); do key KEYCODE_DPAD_RIGHT; sleep 0.25; done
key KEYCODE_DPAD_DOWN; sleep 0.4
for i in $(seq 1 8); do key KEYCODE_DPAD_RIGHT; sleep 0.25; done
for i in $(seq 1 18); do key KEYCODE_DPAD_LEFT; sleep 0.2; done
a dumpsys gfxinfo com.tv98.glow | grep -E "Total frames rendered|Janky frames|90th percentile|99th percentile" | sed 's/^ *//' | while read -r line; do log "gfx $line"; done

log "--- channel zapping (Free TV > Thailand, 6 channels)"
key KEYCODE_HOME; sleep 4
key KEYCODE_DPAD_DOWN; sleep 0.6; key KEYCODE_DPAD_CENTER; sleep 6     # open Free TV
a uiautomator dump /sdcard/ui.xml >/dev/null
rows=$(a cat /sdcard/ui.xml | tr '>' '\n' | grep -oE 'text="[^"]+"' | grep -nE 'text="Thailand"' | head -1 | cut -d: -f1)
# move down the category list until Thailand is selected (recents/favourites may sit above it)
for i in 1 2 3 4; do
    sel=$(a cat /sdcard/ui.xml | tr '>' '\n' | grep -oE 'text="Thailand   ·[^"]*"' | head -1)
    [ -n "$sel" ] && break
    key KEYCODE_DPAD_DOWN; sleep 1.2; a uiautomator dump /sdcard/ui.xml >/dev/null
done
key KEYCODE_DPAD_RIGHT; sleep 0.8
a logcat -c
key KEYCODE_DPAD_CENTER; sleep 14
for i in 1 2 3 4 5; do key KEYCODE_DPAD_UP; sleep 14; done
a logcat -d -s GlowPlayer:I | grep -E 'PLAYING|FAILED' | sed 's/.*GlowPlayer: //' | while read -r line; do log "zap $line"; done
log "glow_playing_pss_kb=$(a dumpsys meminfo com.tv98.glow | awk '/TOTAL:/{print $2; exit}')"
log "used_ram_playing_kb=$(a dumpsys meminfo | grep 'Used RAM' | sed 's/[^0-9]*\([0-9,]*\)K.*/\1/' | tr -d ,)"
key KEYCODE_BACK; sleep 1; key KEYCODE_HOME
log "=== done"
