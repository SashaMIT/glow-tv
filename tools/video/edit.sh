#!/bin/bash
# Cuts the raw tour recording (tools/tourvideo.sh) into a ~35 s promo with guide-style captions.
# Usage: glow/tools/video/edit.sh RAW.mp4 CARDS_DIR OUT.mp4 SEARCH_SCREENSHOT.jpg   (CARDS_DIR from cards.py; needs ffmpeg)
set -euo pipefail
RAW=$1; C=$2; OUT=$3; T=$(mktemp -d); n=0
enc() { ffmpeg -loglevel error -y "$@" -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -r 30 -an "$T/$(printf %02d $n).mp4"; n=$((n+1)); }
# clip START DURATION SPEED CAPTION — the caption eases in from just left of its spot over 0.15 s the first time it
# appears; later clips that carry the same caption show it in place, so it doesn't jump out and back in.
LASTCAP=""
clip() {
  local x="0"
  [ "$4" != "$LASTCAP" ] && x="min(0,-120+t*800)"
  LASTCAP=$4
  enc -ss "$1" -t "$2" -i "$RAW" -loop 1 -i "$C/$4.png" -filter_complex \
    "[0:v]setpts=(PTS-STARTPTS)/$3,fps=30,scale=1280:720,setsar=1[v];[v][1:v]overlay=x='$x':y=0:shortest=1" \
    -t "$(python3 -c "print($2/$3)")"
}
# still IMAGE SECONDS [CAPTION] — slow push-in
still() {
  local cap=${3:-}
  if [ -n "$cap" ]; then
    enc -loop 1 -t "$2" -i "$1" -loop 1 -i "$C/$cap.png" -filter_complex \
      "[0:v]scale=1280:720,zoompan=z='1+0.04*on/($2*30)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=1280x720:fps=30,setsar=1[v];[v][1:v]overlay=x='min(0,-120+t*800)':y=0:shortest=1" -t "$2"
  else
    enc -loop 1 -t "$2" -i "$1" -vf "scale=1280:720,zoompan=z='1+0.03*on/($2*30)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=1280x720:fps=30,setsar=1" -t "$2"
  fi
}
clip 0.3   3.0 1.5 cap1     # Home (straight into it: the title card closes the video)
clip 15.5  6.0 3.0 cap2     # focus TV Guide, open the menu
clip 21.5  9.5 4.0 cap2     # move down the menu to Sports
clip 32.0  6.0 2.5 cap3     # Sports grid, Red Bull TV preview
clip 40.5  6.0 2.0 cap3     # FUEL TV preview, OK
clip 51.0  4.0 1.0 cap4     # BMX on FUEL TV
clip 56.0  1.4 1.0 cap5     # typing 5-0-2
clip 58.4  1.6 6.0 cap5     # connecting
clip 60.0  2.4 1.0 cap5     # Al Jazeera live
clip 130.0 9.0 3.0 cap6     # World headlines
still "$4" 1.8 cap7         # Search, ranked (fresh screenshot)
still "$C/title.png" 1.6
still "$C/end.png" 3.2
ls "$T"/*.mp4 | sed "s/^/file '/; s/$/'/" > "$T/list.txt"
ffmpeg -loglevel error -y -f concat -safe 0 -i "$T/list.txt" -vf "fade=t=in:st=0:d=0.3" \
  -c:v libx264 -preset slow -crf 23 -pix_fmt yuv420p -movflags +faststart "$OUT"
rm -rf "$T"
ffprobe -v error -show_entries format=duration -of default=nw=1 "$OUT"
