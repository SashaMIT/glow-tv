#!/bin/bash
# Turns back on everything box/debloat.sh and box/make-glow-home.sh switched off, and restores the stock Home.
# Usage: box/undo.sh <box-ip:port>
D=${1:?usage: box/undo.sh <box-ip:port>}
adb connect $D
adb -s $D shell pm enable com.android.app.cus.aging
adb -s $D shell pm enable com.softwinner.agingdragonbox
adb -s $D shell pm enable com.softwinner.dragonbox
adb -s $D shell pm enable com.example.a
adb -s $D shell pm enable com.android.egg
adb -s $D shell pm enable com.android.camera2
adb -s $D shell pm enable cm.aptoidetv.pt
adb -s $D shell pm enable com.cetusplay.remoteservice
adb -s $D shell pm enable com.apkpure.aegon
adb -s $D shell pm enable com.flextv.livestore
adb -s $D shell pm enable com.nst.iptvsmarterstvbox
adb -s $D shell settings put global window_animation_scale 1.0
adb -s $D shell settings put global transition_animation_scale 1.0
adb -s $D shell settings put global animator_duration_scale 1.0
adb -s $D shell pm enable com.disney.disneyplus
adb -s $D shell pm enable com.surfshark.vpnclient.android
adb -s $D shell pm enable com.ais.mimo.aisplay.tv
adb -s $D shell pm enable com.doonung.activity
adb -s $D shell pm enable com.instagram.android
adb -s $D shell pm enable com.google.android.katniss
adb -s $D shell pm enable com.google.android.tvrecommendations
adb -s $D shell pm enable com.google.android.leanbacklauncher.recommendations
adb -s $D shell pm enable com.android.tv
adb -s $D shell pm enable com.google.android.leanbacklauncher
adb -s $D shell cmd appops set com.netflix.mediaclient RUN_ANY_IN_BACKGROUND allow
adb -s $D shell pm enable com.google.android.tvlauncher
adb -s $D shell cmd role add-role-holder android.app.role.HOME com.google.android.tvlauncher 0
adb -s $D shell pm revoke com.tv98.glow android.permission.WRITE_SECURE_SETTINGS 2>/dev/null
adb -s $D shell settings put secure screensaver_components com.android.dreams.web/com.android.dreams.web.Screensaver
adb -s $D shell settings put system screen_off_timeout 2147483646
