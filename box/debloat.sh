#!/bin/bash
# Switches off the TV98 box's factory test apps, side-loaded app stores, repackaged streaming apps and Google
# extras that run in the background. Nothing is deleted: box/undo.sh turns everything back on.
# Usage: box/debloat.sh <box-ip:port>   (USB debugging on; see box/README.md)
set -u
D=${1:?usage: box/debloat.sh <box-ip:port>}
adb connect "$D" >/dev/null
off() { for p in "$@"; do printf '%-50s' "$p"; adb -s "$D" shell pm disable-user --user 0 "$p" 2>&1 | tail -1; done; }

# Factory test / burn-in apps
off com.android.app.cus.aging com.softwinner.agingdragonbox com.softwinner.dragonbox com.example.a com.android.egg
# Side-loaded stores and remote-install services (these can install apps without asking)
off cm.aptoidetv.pt com.cetusplay.remoteservice com.apkpure.aegon com.flextv.livestore
# Pre-installed copies of streaming apps that don't come from the official store (reinstall from Google Play if wanted)
off com.disney.disneyplus com.surfshark.vpnclient.android com.ais.mimo.aisplay.tv com.doonung.activity com.instagram.android
# Unused hardware/extras and Google background services
off com.android.camera2 com.google.android.katniss com.google.android.tvrecommendations \
    com.google.android.leanbacklauncher.recommendations com.android.tv
# Snappier menus on the slow GPU
adb -s "$D" shell settings put global window_animation_scale 0.5
adb -s "$D" shell settings put global transition_animation_scale 0.5
adb -s "$D" shell settings put global animator_duration_scale 0.5
# Netflix: don't keep running in the background when you're not watching
adb -s "$D" shell cmd appops set com.netflix.mediaclient RUN_ANY_IN_BACKGROUND ignore
echo "Done. Undo with: box/undo.sh $D"
