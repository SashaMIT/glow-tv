#!/bin/bash
# Records the "What's new" scenes on the box (one short screen recording each) and cuts them into one captioned
# video with a title and an end card.
# Usage: tools/video/whatsnew.sh OUT.mp4 VERSION   (GLOW_DEVICE set; needs ffmpeg and Pillow)
# Leaves the box as it found it: language, Kids mode and test settings are put back after their scenes.
set -uo pipefail
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box}; OUT=$1; VERSION=$2
HERE=$(cd "$(dirname "$0")" && pwd); W=$(mktemp -d); C="$W/cards"; mkdir -p "$C" "$W/raw"
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
  a rm -f /sdcard/s.mp4
  adb -s "$D" shell screenrecord --size 1280x720 --bit-rate 6000000 --time-limit "$secs" /sdcard/s.mp4 &
  local r=$!; sleep 1.2
  "$@"
  wait $r; sleep 1
  adb -s "$D" pull /sdcard/s.mp4 "$W/raw/$name.mp4" >/dev/null && echo "recorded $name"
}

cool
a input keyevent KEYCODE_HOME; sleep 6
s_home() { sleep 1.5; k DPAD_DOWN DPAD_DOWN; sleep 1; k DPAD_DOWN; sleep 1.2; k DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT; sleep 1.5; }
rec home 10 s_home
go .TonightActivity; sleep 5
s_tonight() { sleep 1; k DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 1.5; }
rec tonight 6 s_tonight
go .IntelActivity; sleep 12
s_overview() { sleep 1; k DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_DOWN; sleep 1; }
rec overview 6 s_overview
go .IntelActivity --ei section 1; sleep 10
s_map() { sleep 1; k DPAD_RIGHT; sleep 0.5; k DPAD_RIGHT CHANNEL_UP; sleep 1.2; k DPAD_RIGHT; sleep 0.8; k CHANNEL_UP; sleep 1.5; k DPAD_RIGHT DPAD_RIGHT; sleep 1.5; k CHANNEL_DOWN CHANNEL_DOWN; sleep 1; }
rec map 13 s_map
go .IntelActivity --ei section 3; sleep 8
s_markets() { sleep 4; }
rec markets 5 s_markets
go .IntelActivity --ei section 2; sleep 10
s_sports() { sleep 1; k DPAD_RIGHT DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 0.6; go .F1Activity; sleep 4; k DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 1; }
rec sports 11 s_sports
cool
# Rewind: a news channel for long enough to have kept a minute and more, then hold OK ▸ Back 1 minute.
go .ClassicGuideActivity; sleep 5; a input keyevent KEYCODE_7; sleep 4; k DPAD_DOWN; a input keyevent KEYCODE_DPAD_CENTER; sleep 100
s_rewind() { sleep 0.8; a input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 2; k DPAD_DOWN DPAD_DOWN; sleep 0.8; k DPAD_CENTER; sleep 5; }
rec rewind 11 s_rewind
# Radio: Top worldwide, a music station, once its song and cover have arrived.
go .ClassicGuideActivity --ez radio true; sleep 5; a input keyevent KEYCODE_DPAD_DOWN; sleep 1; a input keyevent KEYCODE_5; sleep 6
k DPAD_DOWN DPAD_DOWN; a input keyevent KEYCODE_DPAD_CENTER; sleep 35
s_radio() { sleep 5; }
rec radio 6 s_radio
a input keyevent KEYCODE_DPAD_CENTER; sleep 1; a input keyevent KEYCODE_HOME; sleep 3
cool
# Thai menus: switched on for the scene, then back to what it was.
LANG_WAS=$(root "grep -o '<string name=\"language\">[a-z]*' $P/display.xml" | sed 's/.*>//' | tr -d '\r')
root "sed -i '/name=\"language\"/d' $P/display.xml; sed -i 's|</map>|    <string name=\"language\">th</string>\n</map>|' $P/display.xml; am force-stop com.tv98.glow"
sleep 3; a input keyevent KEYCODE_HOME; sleep 8
s_thai() { sleep 2; go .ClassicGuideActivity; sleep 4.5; }
rec thai 8 s_thai
root "sed -i '/name=\"language\"/d' $P/display.xml; am force-stop com.tv98.glow"
[ -n "$LANG_WAS" ] && root "sed -i 's|</map>|    <string name=\"language\">$LANG_WAS</string>\n</map>|' $P/display.xml; am force-stop com.tv98.glow"
# Kids mode: on for the scene (no apps picked), the PIN screen, then the box's own Kids settings put back.
root "rm -f /data/local/tmp/kids.bak; [ -f $P/kids.xml ] && cp -p $P/kids.xml /data/local/tmp/kids.bak
echo '<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?><map><boolean name=\"on\" value=\"true\" /></map>' > $P/kids.xml
chown \$(stat -c %u:%g $P/display.xml) $P/kids.xml; chmod 660 $P/kids.xml; restorecon $P/kids.xml; am force-stop com.tv98.glow"
sleep 3; a input keyevent KEYCODE_HOME; sleep 8
s_kids() { sleep 2.5; k DPAD_UP DPAD_UP; sleep 0.8; k DPAD_CENTER; sleep 1; a input keyevent KEYCODE_1; sleep 0.4; a input keyevent KEYCODE_9; sleep 1.5; }
rec kids 8 s_kids
a input keyevent KEYCODE_BACK
root "rm -f $P/kids.xml; [ -f /data/local/tmp/kids.bak ] && cp -p /data/local/tmp/kids.bak $P/kids.xml && rm /data/local/tmp/kids.bak; am force-stop com.tv98.glow"
sleep 3; a input keyevent KEYCODE_HOME; sleep 5
go .NetworkActivity; sleep 3
s_network() { k DPAD_CENTER; sleep 14; }
rec network 16 s_network
a input keyevent KEYCODE_HOME

# ---- cut ----
n=0
enc() { ffmpeg -loglevel error -y "$@" -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -r 30 -an "$W/$(printf %02d $n).mp4"; n=$((n+1)); }
clip() { # clip RAW START DURATION SPEED CAPTION
  enc -ss "$2" -t "$3" -i "$W/raw/$1.mp4" -loop 1 -i "$C/$5.png" -filter_complex \
    "[0:v]setpts=(PTS-STARTPTS)/$4,fps=30,scale=1280:720,setsar=1[v];[v][1:v]overlay=x='min(0,-120+t*800)':y=0:shortest=1" \
    -t "$(python3 -c "print($3/$4)")"
}
still() { enc -loop 1 -t "$2" -i "$1" -vf "scale=1280:720,zoompan=z='1+0.03*on/($2*30)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=1280x720:fps=30,setsar=1" -t "$2"; }
still "$C/newtitle.png" 2.2
clip home     0.5 9   1.6 new1
clip tonight  0.3 5.5 1.3 new1
clip overview 0.3 5.5 1.2 new2
clip map      0.3 12  1.8 new3
clip markets  0.3 4.5 1.0 new4
clip sports   0.3 10  1.6 new5
clip rewind   0.3 10  1.4 new6
clip radio    0.3 5.5 1.0 new7
clip thai     1.5 6.5 1.2 new8
clip kids     1.5 6.5 1.2 new9
clip network  0.3 15  3.0 new10
still "$C/newend.png" 3.5
ls "$W"/[0-9]*.mp4 | sed "s/^/file '/; s/$/'/" > "$W/list.txt"
ffmpeg -loglevel error -y -f concat -safe 0 -i "$W/list.txt" -vf "fade=t=in:st=0:d=0.3" \
  -c:v libx264 -preset slow -crf 24 -pix_fmt yuv420p -movflags +faststart "$OUT"
root "rm -f /data/local/tmp/x.sh" >/dev/null; adb -s "$D" shell rm -f /data/local/tmp/x.sh /sdcard/s.mp4
echo "raw scenes kept in $W/raw"
ffprobe -v error -show_entries format=duration -of default=nw=1 "$OUT"; ls -la "$OUT"
