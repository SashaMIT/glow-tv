#!/bin/bash
# Builds, signs (with glow.keystore on this machine) and publishes a Glow release to GitHub Releases.
# Usage: tools/release.sh 1.0.0 [notes-file]
# The key never leaves this machine; only the signed APK is uploaded. Glow's weekly update check (and Obtainium)
# pick it up from there, and it installs over copies signed with the same key.
set -euo pipefail
cd "$(dirname "$0")/.."
V=${1:?usage: tools/release.sh 1.0.0 [notes-file]}
[[ "$V" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version must look like 1.2.3"; exit 1; }
[ -f glow.keystore ] || { echo "glow.keystore not found: releases must be signed with the key the box's copy uses"; exit 1; }
IFS=. read -r MA MI PA <<< "$V"
export VERSION_NAME="$V" VERSION_CODE=$(( MA * 10000 + MI * 100 + PA ))
./build.sh
OUT="build/glow-$V.apk"
cp build/glow.apk "$OUT"
SDK="${ANDROID_HOME:-$(brew --prefix 2>/dev/null)/share/android-commandlinetools}"
JAVA="${JAVA_HOME:-$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home}/bin/java"
"$JAVA" -jar "$SDK/build-tools/34.0.0/lib/apksigner.jar" verify --print-certs "$OUT" | grep -m1 "SHA-256"
NOTES=${2:-}
if [ -n "$NOTES" ]; then
  gh release create "v$V" "$OUT" --title "Glow $V" --notes-file "$NOTES"
else
  gh release create "v$V" "$OUT" --title "Glow $V" --generate-notes
fi
