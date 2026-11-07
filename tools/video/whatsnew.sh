#!/bin/bash
# The "What's new" video: films Glow's scenes on the box (film.sh, which waits for what's on screen), then cuts them
# into one captioned video with a title and an end card. No screenshots: every scene is filmed.
# Usage: tools/video/whatsnew.sh OUT.mp4 VERSION [RAW_DIR [SCENES]]   (GLOW_DEVICE set; needs ffmpeg and Pillow)
#   RAW_DIR keeps the recordings (a temporary folder if left out); SCENES films only those ("sports radio"), reusing
#   the rest from RAW_DIR; SCENES "none" only cuts.
set -uo pipefail
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box}; OUT=$1; VERSION=$2
HERE=$(cd "$(dirname "$0")" && pwd); W=$(mktemp -d); C="$W/cards"; mkdir -p "$C"
RAW=${3:-$W/raw}; ONLY=${4:-}; mkdir -p "$RAW"
python3 "$HERE/whatsnew_cards.py" "$C" "$VERSION" >/dev/null || exit 1
[ "$ONLY" = none ] || "$HERE/film.sh" "$RAW" "$ONLY"
. "$HERE/cut.sh"
still "$C/newtitle.png" 2.2
clip home        0.5 9   1.6 new1
clip tonight     0.3 5.5 1.3 new1
clip overview    0.3 5.5 1.2 new2
clip map         0.3 11  1.8 new3
clip camgrid     0.3 7   1.6 new4
clip cam         1.0 3.2 1.0 new4   # the first seconds of each cam: filming overloads the box and YouTube's controls show
clip cam         17.5 3  1.0 new4
clip weather     0.3 2.5 1.0 new5
clip radar       0.3 7.5 1.2 new5
clip predictions 0.3 7   1.5 new6
clip sports      0.3 5   1.4 new7
clip f1          0.3 4   1.4 new7
clip banner      0.5 4.5 1.2 new8
clip markets     0.3 3   1.0 new9
clip rewind      1.0 10  1.8 new10
clip radio       0.3 3.2 1.0 new11
clip kids        4.5 5   1.1 new12
clip doorbell    1.0 5   1.2 new13
clip network     0.5 6.5 1.3 new14
still "$C/newend.png" 3.5
ls "$W"/[0-9]*.mp4 | sed "s/^/file '/; s/$/'/" > "$W/list.txt"
ffmpeg -loglevel error -y -f concat -safe 0 -i "$W/list.txt" -vf "fade=t=in:st=0:d=0.3" \
  -c:v libx264 -preset slow -crf 24 -pix_fmt yuv420p -movflags +faststart "$OUT"
echo "raw scenes in $RAW"
ffprobe -v error -show_entries format=duration -of default=nw=1 "$OUT"; ls -la "$OUT"
