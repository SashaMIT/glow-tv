#!/bin/bash
# Installs build/glow.apk on the box and fully pre-compiles it (ART "speed" mode) so it starts and
# scrolls without waiting on the JIT. A plain reinstall would reset it to the slower partial mode.
set -euo pipefail
cd "$(dirname "$0")/.."
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box, e.g. GLOW_DEVICE=192.168.1.50:5555}
adb -s "$D" install -r build/glow.apk
adb -s "$D" shell cmd package compile -m speed -f com.tv98.glow
adb -s "$D" shell dumpsys package dexopt | grep -A2 '\[com.tv98.glow\]' | grep -oE 'status=[a-z-]+' | head -1
