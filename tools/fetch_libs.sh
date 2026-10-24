#!/bin/bash
# Downloads the pinned libraries Glow's player needs (Media3 ExoPlayer + HLS and their runtime deps)
# from Google Maven / Maven Central into libs/, extracting classes.jar (and ProGuard rules) from AARs.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p libs && cd libs

GOOGLE=https://dl.google.com/android/maven2
CENTRAL=https://repo1.maven.org/maven2
M3=1.11.1

fetch() { # repo group artifact version ext
    local path="$(echo "$2" | tr . /)/$3/$4/$3-$4.$5" out="$3-$4.$5"
    [ -f "$out" ] || curl -sfL "$1/$path" -o "$out" || { echo "failed: $1/$path"; exit 1; }
    if [ "$5" = aar ]; then
        unzip -qo "$out" classes.jar -d "x-$3" 2>/dev/null && mv "x-$3/classes.jar" "$3-$4.jar"
        unzip -qo "$out" proguard.txt -d "x-$3" 2>/dev/null && mv "x-$3/proguard.txt" "$3-$4.pro" || true
        rm -rf "x-$3" "$out"
    fi
}

for m in common container datasource decoder extractor database exoplayer exoplayer-hls; do
    fetch $GOOGLE androidx.media3 media3-$m $M3 aar
done
fetch $GOOGLE androidx.annotation annotation-experimental 1.3.1 aar
fetch $GOOGLE androidx.exifinterface exifinterface 1.3.6 aar
fetch $GOOGLE androidx.concurrent concurrent-futures 1.2.0 jar
fetch $CENTRAL com.google.guava guava 33.3.1-android jar
fetch $CENTRAL com.google.guava failureaccess 1.0.2 jar
ls -l
