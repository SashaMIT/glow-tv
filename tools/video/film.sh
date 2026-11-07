#!/bin/bash
# Films Glow's scenes on the box, one short screen recording each, waiting for what's on screen (uiautomator text)
# rather than for a fixed time. Used by whatsnew.sh and promo.sh.
# Usage: tools/video/film.sh RAW_DIR [SCENES]   (GLOW_DEVICE set; SCENES films only those, e.g. "sports radio")
# The box's screen recorder only saves frames when the picture changes, so a still screen comes out as a frame or two.
# Leaves the box as it found it: Kids mode, radio favourites and the stand-in Home Assistant are put back after use.
set -uo pipefail
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box}; RAW=$1; ONLY=${2:-}; mkdir -p "$RAW"
HERE=$(cd "$(dirname "$0")" && pwd); W=$(mktemp -d); MISSED=()
want() { [ -z "$ONLY" ] || [[ " $ONLY " == *" $1 "* ]]; }
a() { adb -s "$D" shell "$@" >/dev/null 2>&1; }
k() { for x in "$@"; do a input keyevent "$x"; sleep 0.5; done; }
go() { a su 0 am start -n "com.tv98.glow/$1" "${@:2}"; }
P=/data/data/com.tv98.glow/shared_prefs
# Runs a few lines as root on the box (pushed as a script: simpler than quoting through adb and su).
root() { printf '%s\n' "$1" > "$W/x.sh"; adb -s "$D" push "$W/x.sh" /data/local/tmp/x.sh >/dev/null; adb -s "$D" shell su 0 sh /data/local/tmp/x.sh; }
cool() { until [ "$(adb -s "$D" shell cat /sys/class/thermal/thermal_zone0/temp | tr -d '\r')" -lt 70000 ]; do sleep 15; done; }
on_screen() { # true when this text (or content description) is on screen
  adb -s "$D" shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  adb -s "$D" shell cat /sdcard/u.xml 2>/dev/null | grep -qF -- "$1"
}
see() { # see TEXT [SECONDS]: waits for it; a miss is reported at the end (the scene is filmed anyway)
  local end=$((SECONDS + ${2:-30}))
  until on_screen "$1"; do
    [ $SECONDS -ge $end ] && { echo "  no \"$1\" after ${2:-30} s"; MISSED+=("$1"); return 1; }
    sleep 1
  done
}
in_log() { # in_log TEXT [SECONDS]: waits for a GlowPerf line (the video player has no text to read)
  local end=$((SECONDS + ${2:-30}))
  until adb -s "$D" logcat -d -s GlowPerf 2>/dev/null | grep -qF -- "$1"; do
    [ $SECONDS -ge $end ] && { echo "  no log \"$1\" after ${2:-30} s"; MISSED+=("$1"); return 1; }
    sleep 1
  done
}
front() { # front ACTIVITY [SECONDS]: waits until that screen has the focus
  local end=$((SECONDS + ${2:-15}))
  until adb -s "$D" shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus' | grep -qF -- "$1"; do
    [ $SECONDS -ge $end ] && { echo "  $1 not in front"; MISSED+=("$1"); return 1; }
    sleep 0.5
  done
}
rec() { # rec NAME SECONDS then the actions to film (a function name)
  local name=$1 secs=$2; shift 2
  a rm -f /sdcard/s.mp4
  adb -s "$D" shell screenrecord --size 1280x720 --bit-rate 6000000 --time-limit "$secs" /sdcard/s.mp4 &
  local r=$!; sleep 1.2
  "$@"
  wait $r; sleep 1
  adb -s "$D" pull /sdcard/s.mp4 "$RAW/$name.mp4" >/dev/null && echo "filmed $name"
}
home() { a input keyevent KEYCODE_WAKEUP; a input keyevent KEYCODE_HOME; front HomeActivity; }
fresh() { a su 0 am force-stop com.tv98.glow; sleep 2; home; see "TONIGHT" 40; }
# A channel playing: guide, its number typed, OK; waits for the player to take the screen. (One digit: that section, one down.)
watch() {
  go .ClassicGuideActivity; front ClassicGuideActivity; sleep 3
  local n=$1; while [ -n "$n" ]; do a input keyevent "KEYCODE_${n:0:1}"; n=${n:1}; done; sleep 3
  [ ${#1} -gt 1 ] || k DPAD_DOWN
  a input keyevent KEYCODE_DPAD_CENTER; front PlayerActivity; sleep "${2:-8}"
}
SPORT=445 # Red Bull TV: free, and good to look at. The banner and rewind scenes play over it, not the news.

cool
if want home; then
  fresh; sleep 6
  s() { sleep 1.5; k DPAD_DOWN DPAD_DOWN; sleep 1; k DPAD_DOWN; sleep 1.2; k DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT; sleep 1.5; }; rec home 10 s
fi
if want guide; then
  go .ClassicGuideActivity; front ClassicGuideActivity; sleep 5; a input keyevent KEYCODE_1; sleep 5
  s() { sleep 0.8; k DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN; k DPAD_RIGHT DPAD_RIGHT; k DPAD_DOWN DPAD_DOWN; sleep 1; }; rec guide 8 s
  home
fi
if want zap; then
  watch 7 10
  s() { sleep 2; k CHANNEL_UP; sleep 3.5; k CHANNEL_UP; sleep 3.5; }; rec zap 11 s
  home
fi
if want tonight; then
  go .TonightActivity; front TonightActivity; see "Tonight" 20; sleep 3
  s() { sleep 1; k DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 1.5; }; rec tonight 6 s
fi
if want overview; then
  go .IntelActivity; see "TOP STORY" 30; sleep 5
  s() { sleep 1; k DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_DOWN; sleep 1; }; rec overview 6 s
fi
if want map; then
  go .IntelActivity --ei section 1; front IntelActivity; see "World map" 30; sleep 8
  s() { sleep 1; k DPAD_RIGHT; sleep 0.5; k DPAD_RIGHT CHANNEL_UP; sleep 1.2; k DPAD_RIGHT; sleep 0.8; k CHANNEL_UP; sleep 1.5; k DPAD_RIGHT DPAD_RIGHT; sleep 1.5; k CHANNEL_DOWN CHANNEL_DOWN; sleep 1; }; rec map 13 s
fi
if want markets; then
  go .IntelActivity --ei section 3; see "Bitcoin" 30; sleep 2
  s() { sleep 4; }; rec markets 5 s
fi
if want camgrid; then
  go .IntelActivity --ei section 4; see "places, live now" 30; sleep 5
  s() { sleep 1; k DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_DOWN DPAD_RIGHT DPAD_RIGHT DPAD_DOWN; sleep 1; }; rec camgrid 8 s
fi
if want cam; then # full screen, ◀ ▶ between cams, waiting for each stream to play (from the giant pandas: daylight in Asia and Africa when filmed)
  a logcat -c
  go .CamActivity --ei cam 2; front CamActivity; in_log "cam playing" 40
  s() { sleep 5; a logcat -c; a input keyevent KEYCODE_DPAD_RIGHT; in_log "cam playing" 25; sleep 4; a logcat -c; a input keyevent KEYCODE_DPAD_RIGHT; in_log "cam playing" 25; sleep 4; }; rec cam 26 s
  a input keyevent KEYCODE_BACK; sleep 2
fi
if want weather; then
  go .IntelActivity --ei section 5; see "°" 30; sleep 2
  s() { sleep 4; }; rec weather 5 s
fi
if want radar; then
  go .IntelActivity --ei section 6; front IntelActivity; see "the last two hours" 30; sleep 5
  s() { sleep 7; }; rec radar 8 s
fi
if want predictions; then
  go .IntelActivity --ei section 7; see "Prediction-market" 30; sleep 3
  s() { sleep 1; k DPAD_RIGHT DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 1; }; rec predictions 8 s
fi
if want sports; then
  go .IntelActivity --ei section 2; front IntelActivity; see "Live scores" 30; sleep 5
  s() { sleep 1; k DPAD_RIGHT DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 1.5; }; rec sports 7 s
fi
if want f1; then
  go .F1Activity; front F1Activity; see "F1 timing   ·" 30; sleep 3
  s() { sleep 1; k DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 1.5; }; rec f1 6 s
fi
cool
if want banner; then # over a channel. A sample banner carrying the latest real Premier League result (or none at all).
  watch $SPORT 8
  LINE=$(python3 "$HERE/latest_score.py")
  s() { sleep 1.5; a su 0 am broadcast -n com.tv98.glow/.SampleBanner --es badge "'PREMIER LEAGUE'" --es headline "'${LINE%%|*}'" --es line "'${LINE##*|}'"; sleep 7; }; rec banner 10 s
fi
if want rewind; then # a minute and more has to be kept first; waits for the OK menu's own words
  want banner || watch $SPORT 8
  sleep 90
  a input keyevent --longpress KEYCODE_DPAD_CENTER; see "Back 1 minute" 15; sleep 1
  s() { sleep 1; k DPAD_DOWN DPAD_DOWN; sleep 0.8; a input keyevent KEYCODE_DPAD_CENTER; sleep 6; }; rec rewind 13 s # (no text to wait for: uiautomator can't read over moving video)
  home
fi
cool
if want radio; then # a music station that sends its song titles, in My stations just for the scene
  root "cp -p $P/radio.xml /data/local/tmp/radio.bak 2>/dev/null
echo '<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?><map><string name=\"favourites\">[{&quot;uuid&quot;:&quot;d1a54d2e-623e-4970-ab11-35f7b56c5ec3&quot;,&quot;name&quot;:&quot;Classic Vinyl HD&quot;,&quot;url&quot;:&quot;https://icecast.walmradio.com:8443/classic&quot;,&quot;logo&quot;:&quot;&quot;,&quot;country&quot;:&quot;US&quot;,&quot;tags&quot;:&quot;1930,1940,1950,1960&quot;,&quot;codec&quot;:&quot;MP3&quot;,&quot;bitrate&quot;:320}]</string></map>' > $P/radio.xml
chown \$(stat -c %u:%g $P/display.xml) $P/radio.xml; chmod 660 $P/radio.xml; restorecon $P/radio.xml; am force-stop com.tv98.glow"
  sleep 3; home; sleep 5
  go .ClassicGuideActivity --ez radio true; front ClassicGuideActivity; sleep 5; a input keyevent KEYCODE_DPAD_CENTER; sleep 4; a input keyevent KEYCODE_DPAD_CENTER; sleep 35
  s() { sleep 6; }; rec radio 7 s
  a input keyevent KEYCODE_DPAD_CENTER; sleep 1; home
  root "rm -f $P/radio.xml; [ -f /data/local/tmp/radio.bak ] && cp -p /data/local/tmp/radio.bak $P/radio.xml && rm /data/local/tmp/radio.bak; am force-stop com.tv98.glow"
  sleep 3; home
fi
if want kids; then # on for the scene (no apps picked), the PIN screen, then the box's own Kids settings put back
  root "rm -f /data/local/tmp/kids.bak; [ -f $P/kids.xml ] && cp -p $P/kids.xml /data/local/tmp/kids.bak
echo '<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?><map><boolean name=\"on\" value=\"true\" /></map>' > $P/kids.xml
chown \$(stat -c %u:%g $P/display.xml) $P/kids.xml; chmod 660 $P/kids.xml; restorecon $P/kids.xml; am force-stop com.tv98.glow"
  sleep 3; home; sleep 8
  s() { sleep 2.5; k DPAD_UP DPAD_UP; sleep 0.8; k DPAD_CENTER; see "Leave Kids mode" 8; a input keyevent KEYCODE_1; sleep 0.4; a input keyevent KEYCODE_9; sleep 1.5; }; rec kids 10 s
  a input keyevent KEYCODE_BACK
  root "rm -f $P/kids.xml; [ -f /data/local/tmp/kids.bak ] && cp -p /data/local/tmp/kids.bak $P/kids.xml && rm /data/local/tmp/kids.bak; am force-stop com.tv98.glow"
  sleep 3; home
fi
if want doorbell; then # a stand-in Home Assistant on this computer (tools/mock_ha.py) rings the bell; the box's settings are put back
  ME=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1)
  python3 "$HERE/../mock_ha.py" 8123 >/dev/null 2>&1 & HA=$!
  root "rm -f /data/local/tmp/ha.bak; [ -f $P/ha.xml ] && cp -p $P/ha.xml /data/local/tmp/ha.bak
echo '<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?><map><string name=\"url\">http://$ME:8123</string><string name=\"token\">test-token</string><string name=\"doorbell\">binary_sensor.front_doorbell</string><string name=\"camera\">camera.front_door</string><string name=\"entities\">[&quot;sensor.living_room_temperature&quot;,&quot;lock.front_door&quot;,&quot;light.kitchen&quot;]</string></map>' > $P/ha.xml
chown \$(stat -c %u:%g $P/display.xml) $P/ha.xml; chmod 660 $P/ha.xml; restorecon $P/ha.xml; am force-stop com.tv98.glow"
  sleep 3; a logcat -c; home; watch 7 12
  s() { sleep 1.5; curl -s -X POST "http://127.0.0.1:8123/mock/ring" >/dev/null; sleep 9; }; rec doorbell 11 s
  home; kill $HA
  root "rm -f $P/ha.xml; [ -f /data/local/tmp/ha.bak ] && cp -p /data/local/tmp/ha.bak $P/ha.xml && rm /data/local/tmp/ha.bak; am force-stop com.tv98.glow"
  sleep 3; home
fi
if want remote; then
  go .RemoteActivity; front RemoteActivity; see "Scan with your phone" 15; sleep 2
  s() { sleep 4; k DPAD_DOWN; sleep 1; }; rec remote 6 s
fi
if want apps; then
  go .AppsActivity; front AppsActivity; sleep 4
  s() { sleep 0.8; k DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 1; }; rec apps 5 s # (▼ only: ◀ ▶ here would reorder the owner's shelf)
fi
if want network; then
  go .NetworkActivity; front NetworkActivity; see "Network" 15
  s() { k DPAD_CENTER; see "delay" 40; sleep 2; }; rec network 26 s
fi
home
a rm -f /sdcard/s.mp4 /sdcard/u.xml; adb -s "$D" shell su 0 rm -f /data/local/tmp/x.sh
if [ ${#MISSED[@]} -gt 0 ]; then echo "MISSED: ${MISSED[*]}"; exit 1; fi
echo "all scenes in $RAW"
