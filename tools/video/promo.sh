#!/bin/bash
# Glow's one-minute promo, the whole system as one product: filmed on the box (film.sh), cut to the beat of its own
# original music (music.py), captions and sound in the one file, ready to share.
# Usage: tools/video/promo.sh OUT.mp4 [RAW_DIR [SCENES]]   (GLOW_DEVICE set; needs ffmpeg, Pillow and numpy)
#   RAW_DIR keeps the recordings; SCENES films only those, reusing the rest; SCENES "none" only cuts.
set -uo pipefail
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box}; OUT=$1
HERE=$(cd "$(dirname "$0")" && pwd); W=$(mktemp -d); C="$W/cards"; mkdir -p "$C"
RAW=${2:-$W/raw}; ONLY=${3:-}; mkdir -p "$RAW"
python3 "$HERE/promo_cards.py" "$C" >/dev/null || exit 1
[ "$ONLY" = none ] || "$HERE/film.sh" "$RAW" "$ONLY"
. "$HERE/cut.sh"
BEAT=$(python3 -c "print(60/104)")
# on RAW START SPEED BEATS CAPTION: a scene lasting a whole number of beats, starting at START in the recording
on() { clip "$1" "$2" "$(python3 -c "print(round($BEAT*$4*$3,3))")" "$3" "$5"; }
pic() { still "$1" "$(python3 -c "print(round($BEAT*$2,3))")"; }
pic "$C/ptitle.png"        4
on home        0.5 1.6 6  p1
on guide       0.3 1.5 6  p2
on zap         1.5 1.0 6  p3
on tonight     0.3 1.3 6  p4
on rewind      7.0 1.3 6  p5
on sports      0.3 1.4 4  p6
on f1          0.3 1.4 4  p7
on banner      1.8 1.0 6  p8
on overview    0.3 1.3 4  p9
on map         0.3 1.8 6  p10
on camgrid     0.3 1.5 2  p11
on cam         0.8 1.0 3  p11
on cam         17.5 1.0 3  p11
on weather     0.3 1.0 2  p12
on radar       0.3 1.2 4  p12
on predictions 0.3 1.5 4  p13
on markets     0.3 1.0 4  p14
on radio       0.3 1.0 4  p15
on kids        7.0 1.1 4  p16
on doorbell    2.8 1.0 4  p17
on remote      0.3 1.0 2  p18
on apps        0.3 1.0 2  p18
pic "$C/pend.png"          8
ls "$W"/[0-9]*.mp4 | sed "s/^/file '/; s/$/'/" > "$W/list.txt"
ffmpeg -loglevel error -y -f concat -safe 0 -i "$W/list.txt" -c copy "$W/all.mp4" </dev/null
LEN=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$W/all.mp4")
python3 "$HERE/music.py" "$W/music.wav" "$LEN" >/dev/null || exit 1
ffmpeg -loglevel error -y -i "$W/all.mp4" -i "$W/music.wav" \
  -vf "fade=t=in:st=0:d=0.3,fade=t=out:st=$(python3 -c "print($LEN-0.5)"):d=0.5" \
  -c:v libx264 -preset slow -crf 22 -pix_fmt yuv420p -c:a aac -b:a 160k -af loudnorm=I=-16:TP=-1.5 -movflags +faststart -shortest "$OUT" </dev/null
echo "raw scenes in $RAW"
ffprobe -v error -show_entries format=duration -of default=nw=1 "$OUT"; ls -la "$OUT"
