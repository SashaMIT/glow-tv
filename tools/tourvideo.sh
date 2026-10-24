#!/bin/bash
# Records a ~2-minute walkthrough of Glow on the box -> glow_tour.mp4 (screenrecord + scripted remote keys).
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box, e.g. GLOW_DEVICE=192.168.1.50:5555}
a() { adb -s "$D" shell "$@" >/dev/null 2>&1; }
k() { for x in "$@"; do a su 0 input keyevent "$x"; sleep 0.45; done; }
a su 0 input keyevent KEYCODE_HOME; sleep 5
a rm -f /sdcard/glow_tour.mp4
adb -s "$D" shell screenrecord --size 1280x720 --bit-rate 5000000 --time-limit 170 /sdcard/glow_tour.mp4 &
REC=$!; sleep 2
sleep 2.5                                     # Home: clock, weather, headlines
k DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT; sleep 1   # apps shelf
k DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_DOWN; sleep 1.5
k DPAD_CENTER; sleep 3                        # TV Guide menu
k DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 1.5
k DPAD_CENTER; sleep 4                        # Sports grid, Red Bull TV preview
k DPAD_DOWN; sleep 5                          # FUEL TV preview
k DPAD_CENTER; sleep 9                        # watch FUEL TV
a su 0 input keyevent KEYCODE_5 KEYCODE_0 KEYCODE_2; sleep 10     # type 502 -> Al Jazeera English
k MENU; sleep 4                               # guide on the same channel
k DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 3
k DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT; sleep 2   # through time
k BACK; sleep 2                               # menu
k KEYCODE_7; sleep 5                          # News
k BACK; sleep 1.5
k KEYCODE_0; sleep 2.5                        # More…
k KEYCODE_5; sleep 6                          # Thailand, with listings
k BACK; sleep 1; k BACK; sleep 1; k BACK; sleep 1.5
a su 0 am start -n com.tv98.glow/.WorldActivity; sleep 5
k DPAD_DOWN DPAD_DOWN DPAD_DOWN; sleep 2
a su 0 am start -n com.tv98.glow/.SearchActivity --es query "sport"; sleep 4
a su 0 am start -n com.tv98.glow/.SettingsActivity; sleep 3
a su 0 input keyevent KEYCODE_HOME; sleep 3
a pkill -INT screenrecord; wait $REC; sleep 2
adb -s "$D" pull /sdcard/glow_tour.mp4 glow_tour.mp4 >/dev/null && a rm -f /sdcard/glow_tour.mp4
ls -la glow_tour.mp4
