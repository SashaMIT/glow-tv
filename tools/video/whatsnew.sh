#!/bin/bash
# Records the "What's new" scenes on the box (one short screen recording each) and cuts them into one captioned
# video with a title and an end card.
# Usage: tools/video/whatsnew.sh OUT.mp4 VERSION [RAW_DIR [SCENES]]   (GLOW_DEVICE set; needs ffmpeg and Pillow)
#   RAW_DIR keeps the recordings (a temporary folder if left out); SCENES films only those ("sports radio"),
#   reusing the rest from RAW_DIR. The box's screen recorder only saves frames when the picture changes, so a
#   still screen is held at its last frame when cut.
# Leaves the box as it found it: language, Kids mode and test settings are put back after their scenes.
set -uo pipefail
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box}; OUT=$1; VERSION=$2
HERE=$(cd "$(dirname "$0")" && pwd); W=$(mktemp -d); C="$W/cards"; mkdir -p "$C"
RAW=${3:-$W/raw}; ONLY=${4:-}; mkdir -p "$RAW"
want() { [ -z "$ONLY" ] || [[ " $ONLY " == *" $1 "* ]]; }
python3 "$HERE/whatsnew_cards.py" "$C" "$VERSION" >/dev/null || exit 1
a() { adb -s "$D" shell "$@" >/dev/null 2>&1; }
k() { for x in "$@"; do a input keyevent "$x"; sleep 0.5; done; }
go() { a su 0 am start -n "com.tv98.glow/$1" "${@:2}"; }
P=/data/data/com.tv98.glow/shared_prefs
# Runs a few lines as root on the box (pushed as a script: simpler than quoting through adb and su).
root() { printf '%s\n' "$1" > "$W/x.sh"; adb -s "$D" push "$W/x.sh" /data/local/tmp/x.sh >/dev/null; adb -s "$D" shell su 0 sh /data/local/tmp/x.sh; }
cool() { # benchmarks and recordings heat the box: wait under 70 °C
  until [ "$(adb -s "$D" shell cat /sys/class/thermal/thermal_zone0/temp)" -lt 70000 ]; do sleep 15; done; }
# rec NAME SECONDS then the actions to film (a function name)
rec() {
  local name=$1 secs=$2; shift 2
  want "$name" || return 0
  a rm -f /sdcard/s.mp4
  adb -s "$D" shell screenrecord --size 1280x720 --bit-rate 6000000 --time-limit "$secs" /sdcard/s.mp4 &
  local r=$!; sleep 1.2
  "$@"
  wait $r; sleep 1
  adb -s "$D" pull /sdcard/s.mp4 "$RAW/$name.mp4" >/dev/null && echo "recorded $name"
}

cool
a input keyevent KEYCODE_HOME; sleep 6
s_home() { sleep 1.5; k DPAD_DOWN DPAD_DOWN; sleep 1; k DPAD_DOWN; sleep 1.2; k DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT; sleep 1.5; }
rec home 10 s_home
want tonight && { go .TonightActivity; sleep 5; }
s_tonight() { sleep 1; k DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 1.5; }
rec tonight 6 s_tonight
want overview && { go .IntelActivity; sleep 12; }
s_overview() { sleep 1; k DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_DOWN; sleep 1; }
rec overview 6 s_overview
want map && { go .IntelActivity --ei section 1; sleep 10; }
s_map() { sleep 1; k DPAD_RIGHT; sleep 0.5; k DPAD_RIGHT CHANNEL_UP; sleep 1.2; k DPAD_RIGHT; sleep 0.8; k CHANNEL_UP; sleep 1.5; k DPAD_RIGHT DPAD_RIGHT; sleep 1.5; k CHANNEL_DOWN CHANNEL_DOWN; sleep 1; }
rec map 13 s_map
want markets && { go .IntelActivity --ei section 3; sleep 8; }
s_markets() { sleep 4; }
rec markets 5 s_markets
want sports && { go .IntelActivity --ei section 2; sleep 10; go .F1Activity; sleep 6; a input keyevent KEYCODE_BACK; sleep 2; }
s_sports() { sleep 1; k DPAD_RIGHT DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 0.6; go .F1Activity; sleep 3; k DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 1.5; }
rec sports 12 s_sports
cool
# Rewind: a news channel for long enough to have kept a minute and more, then hold OK ▸ Back 1 minute.
want rewind && { a su 0 am force-stop com.tv98.glow; sleep 2; a input keyevent KEYCODE_HOME; sleep 8
  go .ClassicGuideActivity; sleep 6; a input keyevent KEYCODE_7; sleep 4; k DPAD_DOWN; a input keyevent KEYCODE_DPAD_CENTER; sleep 100; }
s_rewind() { sleep 0.8; a input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 3.5; k DPAD_DOWN DPAD_DOWN; sleep 0.8; k DPAD_CENTER; sleep 6; }
rec rewind 14 s_rewind
# Radio: a music station that sends its song titles (Classic Vinyl HD), put in My stations just for the scene.
if want radio; then
  root "cp -p $P/radio.xml /data/local/tmp/radio.bak 2>/dev/null
echo '<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?><map><string name=\"favourites\">[{&quot;uuid&quot;:&quot;d1a54d2e-623e-4970-ab11-35f7b56c5ec3&quot;,&quot;name&quot;:&quot;Classic Vinyl HD&quot;,&quot;url&quot;:&quot;https://icecast.walmradio.com:8443/classic&quot;,&quot;logo&quot;:&quot;&quot;,&quot;country&quot;:&quot;US&quot;,&quot;tags&quot;:&quot;1930,1940,1950,1960&quot;,&quot;codec&quot;:&quot;MP3&quot;,&quot;bitrate&quot;:320}]</string></map>' > $P/radio.xml
chown \$(stat -c %u:%g $P/display.xml) $P/radio.xml; chmod 660 $P/radio.xml; restorecon $P/radio.xml; am force-stop com.tv98.glow"
  sleep 3; a input keyevent KEYCODE_HOME; sleep 6
  go .ClassicGuideActivity --ez radio true; sleep 6; a input keyevent KEYCODE_DPAD_CENTER; sleep 4; a input keyevent KEYCODE_DPAD_CENTER; sleep 40
fi
s_radio() { sleep 6; }
rec radio 7 s_radio
if want radio; then
  a input keyevent KEYCODE_DPAD_CENTER; sleep 1; a input keyevent KEYCODE_HOME; sleep 2
  root "rm -f $P/radio.xml; [ -f /data/local/tmp/radio.bak ] && cp -p /data/local/tmp/radio.bak $P/radio.xml && rm /data/local/tmp/radio.bak; am force-stop com.tv98.glow"
  sleep 3; a input keyevent KEYCODE_HOME; sleep 3
fi
cool
# Thai menus: switched on for the scene, then back to what it was.
if want thai; then
LANG_WAS=$(root "grep -o '<string name=\"language\">[a-z]*' $P/display.xml" | sed 's/.*>//' | tr -d '\r')
root "sed -i '/name=\"language\"/d' $P/display.xml; sed -i 's|</map>|    <string name=\"language\">th</string>\n</map>|' $P/display.xml; am force-stop com.tv98.glow"
sleep 3; a input keyevent KEYCODE_HOME; sleep 8
s_thai() { sleep 2; go .ClassicGuideActivity; sleep 4.5; }
rec thai 8 s_thai
root "sed -i '/name=\"language\"/d' $P/display.xml; am force-stop com.tv98.glow"
[ -n "$LANG_WAS" ] && root "sed -i 's|</map>|    <string name=\"language\">$LANG_WAS</string>\n</map>|' $P/display.xml; am force-stop com.tv98.glow"
fi
# Kids mode: on for the scene (no apps picked), the PIN screen, then the box's own Kids settings put back.
if want kids; then
root "rm -f /data/local/tmp/kids.bak; [ -f $P/kids.xml ] && cp -p $P/kids.xml /data/local/tmp/kids.bak
echo '<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?><map><boolean name=\"on\" value=\"true\" /></map>' > $P/kids.xml
chown \$(stat -c %u:%g $P/display.xml) $P/kids.xml; chmod 660 $P/kids.xml; restorecon $P/kids.xml; am force-stop com.tv98.glow"
sleep 3; a input keyevent KEYCODE_HOME; sleep 8
s_kids() { sleep 2.5; k DPAD_UP DPAD_UP; sleep 0.8; k DPAD_CENTER; sleep 1; a input keyevent KEYCODE_1; sleep 0.4; a input keyevent KEYCODE_9; sleep 1.5; }
rec kids 8 s_kids
a input keyevent KEYCODE_BACK
root "rm -f $P/kids.xml; [ -f /data/local/tmp/kids.bak ] && cp -p /data/local/tmp/kids.bak $P/kids.xml && rm /data/local/tmp/kids.bak; am force-stop com.tv98.glow"
sleep 3; a input keyevent KEYCODE_HOME; sleep 5
fi
want network && { go .NetworkActivity; sleep 4; }
s_network() { k DPAD_CENTER; sleep 20; }
rec network 23 s_network
a input keyevent KEYCODE_HOME

# ---- cut ----
n=0
enc() { ffmpeg -loglevel error -y "$@" -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -r 30 -an "$W/$(printf %02d $n).mp4" </dev/null; n=$((n+1)); }
clip() { # clip RAW START DURATION SPEED CAPTION — still stretches repeat their frame, and the end holds.
  # Inputs are time-limited: a recording of a still screen can be one frame with no length, which kept ffmpeg
  # waiting for ever when the caption picture looped without an end.
  local out; out=$(python3 -c "print(round($3/$4, 3))")
  if [ "$(ffprobe -v error -count_frames -select_streams v -show_entries stream=nb_read_frames -of csv=p=0 "$RAW/$1.mp4")" -lt 3 ]; then
    ffmpeg -loglevel error -y -i "$RAW/$1.mp4" -frames:v 1 "$W/$1.png" </dev/null
    stillc "$W/$1.png" "$out" "$5"; return
  fi
  enc -t 40 -i "$RAW/$1.mp4" -loop 1 -t "$out" -i "$C/$5.png" -filter_complex \
    "[0:v]fps=30,tpad=stop_mode=clone:stop_duration=30,trim=start=$2:duration=$3,setpts=(PTS-STARTPTS)/$4,scale=1280:720,setsar=1[v];[v][1:v]overlay=x='min(0,-120+t*800)':y=0" \
    -t "$out"
}
stillc() { # a picture with its caption, slow push-in
  enc -loop 1 -t "$2" -i "$1" -loop 1 -t "$2" -i "$C/$3.png" -filter_complex \
    "[0:v]scale=1280:720,zoompan=z='1+0.04*on/($2*30)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=1280x720:fps=30,setsar=1[v];[v][1:v]overlay=x='min(0,-120+t*800)':y=0" -t "$2"
}
still() { enc -loop 1 -t "$2" -i "$1" -vf "scale=1280:720,zoompan=z='1+0.03*on/($2*30)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=1280x720:fps=30,setsar=1" -t "$2"; }
still "$C/newtitle.png" 2.2
clip home     0.5 9   1.6 new1
clip tonight  0.3 5.5 1.3 new1
clip overview 0.3 5.5 1.2 new2
clip map      0.3 12  1.8 new3
clip markets  0.3 4.5 1.0 new4
clip sports   0.3 11  1.6 new5
clip rewind   0.3 13  1.6 new6
clip radio    0.3 6   1.0 new7
clip thai     1.5 6.5 1.2 new8
clip kids     1.5 6.5 1.2 new9
clip network  0.3 21  3.5 new10
still "$C/newend.png" 3.5
ls "$W"/[0-9]*.mp4 | sed "s/^/file '/; s/$/'/" > "$W/list.txt"
ffmpeg -loglevel error -y -f concat -safe 0 -i "$W/list.txt" -vf "fade=t=in:st=0:d=0.3" \
  -c:v libx264 -preset slow -crf 24 -pix_fmt yuv420p -movflags +faststart "$OUT"
root "rm -f /data/local/tmp/x.sh" >/dev/null; adb -s "$D" shell rm -f /data/local/tmp/x.sh /sdcard/s.mp4
echo "raw scenes in $RAW"
ffprobe -v error -show_entries format=duration -of default=nw=1 "$OUT"; ls -la "$OUT"
