#!/bin/bash
# Screenshot tour of every Glow screen -> docs/screenshots/NN_name.png. Usage: tools/tour.sh [outdir]
D=${GLOW_DEVICE:?set GLOW_DEVICE to your box, e.g. GLOW_DEVICE=192.168.1.50:5555}; OUT=${1:-docs/screenshots}; mkdir -p "$OUT"
a() { adb -s "$D" shell "$@" >/dev/null 2>&1; }
k() { a su 0 input keyevent "$@"; }
go() { a su 0 am start -n "com.tv98.glow/.$1" "${@:2}"; }
shot() { adb -s "$D" exec-out screencap -p > "$OUT/$1.png"; echo "$1"; }
k KEYCODE_HOME; sleep 6;                                   shot 01_home
k DPAD_DOWN; sleep 1;                                      shot 02_home_live_tv
go ClassicGuideActivity; sleep 3;                          shot 03_guide_menu
k KEYCODE_6; sleep 6;                                      shot 04_guide_sports
k DPAD_DOWN DPAD_DOWN; sleep 5;                            shot 05_guide_sports_preview
k BACK; sleep 1; k KEYCODE_7; sleep 6;                     shot 06_guide_news
k DPAD_RIGHT DPAD_RIGHT; sleep 2;                          shot 07_guide_news_later
k BACK; sleep 1; k KEYCODE_5; sleep 5;                     shot 08_guide_movies
k BACK; sleep 1; k KEYCODE_0; sleep 1;                     shot 09_guide_more
k KEYCODE_5; sleep 7;                                      shot 10_guide_thailand
k DPAD_CENTER; sleep 8;                                    shot 11_player
k KEYCODE_DPAD_LEFT; sleep 1;                              shot 12_player_banner
k KEYCODE_4 KEYCODE_0 KEYCODE_2; sleep 0.6;                shot 13_player_typing_number
sleep 9;                                                   shot 14_player_fuel_tv
k BACK; sleep 1; k BACK; k BACK; k BACK
go ChannelsActivity --es source free; sleep 5;             shot 15_free_tv_list
go SearchActivity --es query "news"; sleep 4;              shot 16_search
go WorldActivity; sleep 6;                                 shot 17_world_headlines
go SettingsActivity; sleep 2;                              shot 18_settings
go LoginActivity; sleep 2;                                 shot 19_iptv_login
k KEYCODE_HOME; sleep 2
