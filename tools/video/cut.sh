# Cutting helpers shared by whatsnew.sh and promo.sh: source this with W (work folder), C (cards) and RAW set.
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
