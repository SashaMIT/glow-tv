#!/bin/bash
# Makes Glow the box's Home screen and lets its Settings switch developer access off and on.
# Run after tools/install.sh. Undo with box/undo.sh.
set -eu
D=${1:?usage: box/make-glow-home.sh <box-ip:port>}
# Lets Glow ▸ Settings ▸ Developer access turn USB/network debugging off (and back on) in two clicks.
adb -s "$D" shell pm grant com.tv98.glow android.permission.WRITE_SECURE_SETTINGS
# The stock launchers would otherwise keep taking Home back.
adb -s "$D" shell pm disable-user --user 0 com.google.android.tvlauncher || true
adb -s "$D" shell pm disable-user --user 0 com.google.android.leanbacklauncher || true
adb -s "$D" shell cmd role add-role-holder android.app.role.HOME com.tv98.glow 0 || true
adb -s "$D" shell input keyevent KEYCODE_HOME
