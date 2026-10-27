# Glow

A home screen and live TV app for cheap Android TV boxes. Glow turns a slow, cluttered "Ultra HD" box into
something that feels like a proper set-top box: a clean home screen, a classic blue satellite-style TV guide
with channel numbers, a player that keeps trying until a channel plays, a phone remote, and world headlines.

It was built for (and on) a TV98 box: Allwinner H616, 1 GB of RAM, Android 10. The whole app is 1.2 MB.

![Glow in 20 seconds: Home with Intelligence, the live world map and rain, live sport, cams, rain radar, the TV guide with listings, Now & Next, subtitles and a score banner](docs/glow-tour.gif)

**[What's new in 1.5: a 56-second video (MP4)](docs/glow-whatsnew.mp4)** · [The first video (MP4)](docs/glow-promo.mp4) · [The TV guide in motion (GIF)](docs/glow-guide.gif) · [All screenshots](docs/screenshots) · [Measured results](docs/BENCHMARKS.md) · [Roadmap](ROADMAP.md)

## What it does

- **Home screen**: clock, local weather, a rolling world headline, icon buttons (search, world, phone, IPTV,
  settings, and a red shield while developer access is on), your apps as full-colour cards, and live TV: the TV
  Guide (TV and radio) followed by your favourites, with channel numbers.
- **TV guide**: a numbered menu (All Channels, Favourites, Entertainment, Lifestyle & Culture, Movies, Sports,
  News, Documentaries, Kids, 0 More…), then channels against half-hour columns with a muted live preview.
  Type a number to jump; ◀ ▶ moves through the day; OK on a later programme sets a reminder; channel logos and
  a one-line summary of the highlighted programme; colour keys for Recently watched, Search, World and Favourites.
- **Channel numbers and ranking**: every channel gets a number in its genre's block (Entertainment 1xx,
  Lifestyle 2xx, Movies 3xx, Sports 4xx, News 5xx, Documentaries 6xx, Kids 7xx, Music 8xx). Inside each genre,
  hand-picked top channels come first (Red Bull TV, FUEL TV and the fight channels in Sports; Al Jazeera
  English, CNA, DW, France 24, NBC News NOW and ABC News Live in News), then English and Thai feeds before other
  languages, with regional copies and local stations below the main channel. Numbers are saved, so they don't move.
- **Player** (ExoPlayer): if a link is dead it tries the channel's other links, then the broadcaster's official
  stream, then its official YouTube live. The channels above and below are prepared silently, so ▲ and ▼ change
  channel in about 0.3 s. Hold OK for the Player menu: pause and rewind, back to live, picture size, night sound
  (evens out loud and quiet), dialogue boost (clearer voices), a corner picture of another channel, stats. ◀ audio
  and subtitles, ▶ sleep timer. **Rewind for minutes, not seconds**: Glow keeps what you watch (from what the player
  downloads anyway) so you can pause and go back past the channel's own 10–30 s; on a USB stick if one is plugged in,
  else in spare internal storage (never below Android's low-storage line), given back when you stop watching.
- **Free TV**: about 2,300 working channels from the public [iptv-org](https://github.com/iptv-org/iptv) lists
  (Thailand, UK, US, world news, sports), link-checked every 12 hours so dead ones stay hidden.
- **Programme listings**: about 1,000 channels. Every night a GitHub Actions job in this repo runs
  [iptv-org/epg](https://github.com/iptv-org/epg)'s grabbers for the channels Glow lists and publishes one trimmed
  file (~1.5 MB) to the `data` branch, matched on iptv-org ids; plus Pluto TV's own schedules
  ([i.mjh.nz](https://github.com/matthuisman/i.mjh.nz)), UK/US from [epg.pw](https://epg.pw), and Thai channels from
  TrueID. The same job writes channel facts from [iptv-org's database](https://github.com/iptv-org/api) (language,
  closed, replaced, adult), so closed channels disappear and rankings use real languages.
- **Radio** (the guide's RADIO tab): thousands of free stations from [radio-browser.info](https://www.radio-browser.info)
  in ten numbered sections (Thailand, Thai music, UK, US, top worldwide, news & talk, chill, jazz, classical, dance),
  listed like TV channels with logos and the song on air; hold OK to add a station to **My stations**; the song's
  cover (Apple's iTunes search) shows while you listen and on the dimmed screen. A Radio card on Home goes straight
  there.
- **Favourite teams, Resume, Cast, sleep timer**: pick teams in Settings (their games first, score banners for them
  only if you like); the last channel watched is first in Live TV with a Resume badge; the Cast button opens the
  box's Miracast receiver so a phone can mirror to "TV98"; ▶ while watching sets a sleep timer (30–120 min) that
  stops TV and starts the screensaver.
- **Phone remote**: open the box's address on your phone, enter the 4-digit code shown on the TV, then use a
  D-pad, type searches and logins, open favourites. Home network only; switches itself off after 30 minutes.
- **Intelligence** (Home row and screen): a live world map drawn by Glow (earthquakes, current disaster alerts,
  wildfires and storms, places in the news, webcams, the International Space Station, day and night), 22 live city
  and space cams one click away (ended streams are spotted and skipped), Sports now (live scores, fixtures and
  results for UFC, F1, Premier League, Champions League, NBA and NFL, with free sports channels to watch), a weather
  page with air quality and 7 days, a rain radar for the last two hours, rain across the world on the map, and what
  prediction markets expect. While you watch TV, small banners show goals, kick-offs and results in your leagues
  (24 to choose from in Settings, including ATP and WTA tennis and three golf tours).
  OK on anything on the map tunes to a news channel covering that part of the world; CH+ zooms in on it (2×, 4×),
  CH− or Back zooms out. **Overview** puts the top of every source on one screen; **Markets** has Bitcoin, Ether
  and gold (CoinGecko) and the baht, pound and euro (European Central Bank) with their last days; OK on F1 in
  Sports now opens the **F1 timing sheet** of the latest session (OpenF1: positions, gaps, laps, tyres; their live
  timing is paid, so it arrives after each session). All from free public sources.
- **World**: top stories and alerts by region from [World Monitor](https://github.com/koala73/worldmonitor), with
  article pictures, a QR code to read the story on your phone, and OK to watch a news channel covering that region.
- **My IPTV**: sign in with your own Xtream-style provider details to browse and play their channels.
- **Screensaver**: after 15 minutes idle, a big clock, weather, world stories with their pictures and what's on your
  favourites, drifting slowly so nothing burns in.
- **Remote buttons & Quick settings** (Settings): give spare remote buttons a job (an app, TV Guide, Sports now,
  Quick settings…) for press and hold; Quick settings has night sound, subtitles, picture size, sleep timer, Wi-Fi.
  Score alerts can show over other apps once "display over other apps" is allowed.
- **Tonight on free TV** (Home row, TV Guide ▸ TONIGHT): films, live sport, premieres and news specials from 18:00
  to midnight, picked from the listings Glow already has (no keys), favourite channels and teams first. Replays,
  highlights, loop channels and pay-TV re-streams are left out. OK watches what's on now or sets a reminder.
- **Network & data** (Settings): Wi-Fi signal and link speed, a speed test, data used today and this month.
- **Home Assistant** (phone remote ▸ More): connect your own Home Assistant (address + long-lived token, kept on the
  box) for a Home card with the sensors you pick, and the door camera over whatever's on when the doorbell rings.
- **Kids mode** (Settings): a simpler Home for children with only the apps you pick and the children's channels
  (PBS Kids, Nick Jr., cartoons…); the TV Guide opens on Kids, and Search, news, Intelligence and Settings stay
  closed until a 4-digit PIN is entered (five wrong tries, then a minute's wait). It's a friendly fence for young
  children, not a lock: Android's own settings can still reset Glow.
- **ภาษาไทย / Thai menus** (Settings ▸ Language: Automatic, English, ไทย). Channel names, programmes and news stay
  as the broadcasters send them.
- **Extra apps** (Settings): SmartTube, Jellyfin, Kodi and NewPipe from their developers' official releases,
  installed only when you pick one (Android confirms each install).
- **Photos and settings backup** (phone remote ▸ More): send photos from your phone to the screensaver; download
  your Glow settings to the phone and restore them later (never your IPTV sign-in).
- **Settings**: developer access (ADB) off and on, offline channels, phone remote, screensaver, weather city,
  12/24-hour clock, Thai channels first, and which apps appear on Home and in what order.

| | | |
|---|---|---|
| ![Home](docs/screenshots/v2_after_home_header.jpg) | ![Guide menu](docs/screenshots/03_guide_menu.jpg) | ![Sports](docs/screenshots/04_guide_sports.jpg) |
| ![News](docs/screenshots/06_guide_news.jpg) | ![Thailand with listings](docs/screenshots/10_guide_thailand.jpg) | ![FUEL TV playing](docs/screenshots/14_player_fuel_tv.jpg) |
| ![Search](docs/screenshots/16_search.jpg) | ![World headlines](docs/screenshots/v2_after_world3.jpg) | ![Entertainment with listings and logos](docs/screenshots/v2_after_entertainment.jpg) |
| ![Home with the Intelligence row](docs/screenshots/20_home_intelligence.jpg) | ![World map](docs/screenshots/21_intel_world_map.jpg) | ![Live cams](docs/screenshots/23_intel_live_cams.jpg) |
| ![Disaster alerts](docs/screenshots/22_intel_alerts.jpg) | ![Weather](docs/screenshots/24_intel_weather.jpg) | ![Predictions](docs/screenshots/25_intel_predictions.jpg) |
| ![Sports now](docs/screenshots/27_intel_sports_now.jpg) | ![Rain radar](docs/screenshots/28_intel_rain_radar.jpg) | ![Live TV cards](docs/screenshots/26_live_tv_cards.jpg) |
| ![Rain around the world](docs/screenshots/29_world_rain.jpg) | ![Sports leagues](docs/screenshots/30_sports_leagues.jpg) | ![Score banner over live TV](docs/screenshots/31_score_banner.jpg) |
| ![News with nightly listings](docs/screenshots/32_guide_news_listings.jpg) | ![Favourite team first](docs/screenshots/33_favourite_team.jpg) | ![Cast button and Resume card](docs/screenshots/34_cast_and_resume.jpg) |
| ![Sleep timer](docs/screenshots/35_sleep_timer.jpg) | ![Now & Next](docs/screenshots/36_now_and_next.jpg) | ![Subtitles](docs/screenshots/37_subtitles.jpg) |
| ![Audio & subtitles](docs/screenshots/38_audio_subtitles_menu.jpg) | ![Live sport now](docs/screenshots/39_live_sport_now.jpg) | ![Extra apps](docs/screenshots/40_extra_apps.jpg) |
| ![Photo on the screensaver](docs/screenshots/41_screensaver_photo.jpg) | ![Player menu](docs/screenshots/42_player_menu.jpg) | ![Corner picture](docs/screenshots/43_corner_picture.jpg) |
| ![Stats overlay](docs/screenshots/44_stats_overlay.jpg) | ![Remote buttons](docs/screenshots/45_remote_buttons.jpg) | ![Quick settings](docs/screenshots/46_quick_settings.jpg) |
| ![Kids mode Home](docs/screenshots/47_kids_home.jpg) | ![Kids mode PIN](docs/screenshots/48_kids_pin.jpg) | ![Home in Thai](docs/screenshots/49_thai_home.jpg) |
| ![TV Guide in Thai](docs/screenshots/50_thai_guide.jpg) | ![Guide grid in Thai](docs/screenshots/51_thai_guide_grid.jpg) | ![Intelligence overview](docs/screenshots/52_intel_overview.jpg) |
| ![Markets](docs/screenshots/53_intel_markets.jpg) | ![F1 timing sheet](docs/screenshots/54_f1_timing.jpg) | ![World map zoomed in](docs/screenshots/55_map_zoom.jpg) |
| ![Tennis and golf](docs/screenshots/56_tennis_golf.jpg) | ![Overview in Thai](docs/screenshots/57_thai_overview.jpg) | ![Rewound a minute](docs/screenshots/58_rewind_minute.jpg) |
| ![Radio with the song's cover](docs/screenshots/59_radio_cover.jpg) | ![Radio, dimmed](docs/screenshots/60_radio_dimmed.jpg) | ![TV Guide rows](docs/screenshots/61_guide_rows.jpg) |
| ![Network & data](docs/screenshots/62_network.jpg) | ![Home Assistant card](docs/screenshots/63_home_assistant_card.jpg) | ![Doorbell pop-up](docs/screenshots/64_doorbell.jpg) |
| ![Tonight on free TV](docs/screenshots/65_tonight_page.jpg) | ![Tonight row on Home](docs/screenshots/66_tonight_row.jpg) | |

## Measured on the box

| | Before | After |
|---|---|---|
| Loading the channel lists | 1,430 ms | 70 ms |
| Opening the TV guide | 3,198 ms | 147 ms |
| Changing channel (median of 20) | 2,395 ms | 1,984 ms |
| Giving up on a dead channel | ~27 s | 11 s |
| Top sports & news picks that play | — | 15 / 15 |
| Glow's memory on Home | — | 77 MB (with the Intelligence row; smooth scrolling layers included) |

Every change has a before/after in [docs/BENCHMARKS.md](docs/BENCHMARKS.md), including what didn't work.

## Build

Glow builds with the plain Android SDK tools, no Gradle. On macOS:

```bash
brew install openjdk@17 android-commandlinetools python3
sdkmanager "build-tools;34.0.0" "platforms;android-35"
./build.sh
```

The first build downloads the player libraries (Media3 ExoPlayer 1.11.1 and its dependencies, pinned in
`tools/fetch_libs.sh`), generates `WmCatalog.java` from World Monitor's channel catalogue, and creates a signing
key (`glow.keystore`, kept out of git; set `GLOW_KS_PASS` to choose its password). The APK lands in `build/glow.apk`.

## Install on a box

**Keep it updated:** Glow checks GitHub Releases weekly and offers updates in Settings. With
[Obtainium](https://github.com/ImranR98/Obtainium) on the box you can also add `https://github.com/SashaMIT/glow-tv`
and it will track releases for you. [Latest release](https://github.com/SashaMIT/glow-tv/releases/latest).

1. On the box, turn on developer options and USB/network debugging. Many cheap boxes expose ADB on port 5555 or
   8888 once it's on.
2. From your computer:

```bash
export GLOW_DEVICE=192.168.1.50:5555   # your box's address and ADB port
adb connect $GLOW_DEVICE
tools/install.sh                       # installs and fully pre-compiles Glow
box/make-glow-home.sh $GLOW_DEVICE     # makes Glow the Home screen (and lets its Settings manage ADB and the screensaver)
box/debloat.sh $GLOW_DEVICE            # optional: switches off factory test apps, side-loaded stores and extras
```

`box/undo.sh` turns everything back on and restores the stock Home screen. Nothing is deleted.

3. **Switch developer access off when you're done** (Glow ▸ Settings ▸ Developer access). While it's on, anyone on
   your Wi-Fi can fully control the box; Glow shows a warning on Home until it's off.

## Channels and content

Glow doesn't host, bundle or sell any streams. It plays freely published streams from the public iptv-org lists
and broadcasters' own links and official YouTube channels. Pay-TV channels that only appear in those lists as
unofficial re-streams (ESPN, CNN, Fox News Channel, beIN Sports 1–8 and similar) are never promoted. Glow doesn't
get around any sign-in or subscription: My IPTV needs your own provider account. TLS certificate checks are never
switched off.

## Project layout

| Path | What's there |
|---|---|
| `src/com/tv98/glow/` | The app. `HomeActivity`, `ClassicGuideActivity` + `GuideGridView` (guide, drawn as one view), `GuideLineup` + `Genres` (numbers and ranking), `PlayerActivity` + `Sources` + `OfficialLive` (playback and fallbacks), `FreeTv` + `Health` (channel lists and link checks), `Guide` (listings), `Remote` (phone remote), `World`, `Weather`, `IntelActivity` + `Intel` + `WorldMapView` + `Sports` + `ScoreAlerts` + `Cams` + `RadarView` + `Rain` + `Markets` + `F1Timing` (Intelligence), `Kids` + `PinActivity` (Kids mode), `L` (menu language) |
| `res/raw/remote.html` | The phone remote's web page |
| `res/raw/lang_th.tsv` | Thai menus: one English phrase and its Thai per line (see Translating) |
| `.github/workflows/` | `release.yml` (signed releases), `guide.yml` (nightly listings + channel facts to the `data` branch) |
| `tools/` | `build_guide_data.py` / `trim_guide.py` (nightly data), build helpers, install, benchmarks (`bench.sh`, `zapbench.sh`, `guidebench.sh`, `scrollbench.sh`), a mock IPTV server for testing logins, the screenshot tour and video scripts |
| `box/` | Debloat, make-Home and undo scripts for the TV98 box |
| `docs/` | Screenshots, tour video, benchmarks |
| `ROADMAP.md` | What's next, top picks from GitHub, and what Glow won't do |

## Translating

Glow's English stays in the code; each language is one file in `res/raw/` with an English phrase and its translation
per line, separated by a tab (`lang_th.tsv` is Thai). `{n}` stands for a number, `{t}` for a time or date and `{}` for
a name, e.g. `Sleep in {n} min<TAB>ปิดใน {} นาที`. Symbols in front (★, ▶, ⏰) are kept as they are, so leave them
out. `L.java` applies it wherever text is shown. Corrections and new languages are welcome as pull requests.

## Credits

- [AndroidX Media3 / ExoPlayer](https://github.com/androidx/media) and [Guava](https://github.com/google/guava): Apache-2.0, downloaded at build time
- [QR Code generator](https://www.nayuki.io/page/qr-code-generator-library) by Project Nayuki: MIT, included in `src/io/nayuki/qrcodegen`
- [iptv-org/iptv](https://github.com/iptv-org/iptv): channel lists (Unlicense), fetched by the app
- [World Monitor](https://github.com/koala73/worldmonitor): headlines and the news-channel catalogue (AGPL-3.0); the catalogue is downloaded and turned into `WmCatalog.java` at build time and is not part of this repository
- [epg.pw](https://epg.pw), [i.mjh.nz](https://github.com/matthuisman/i.mjh.nz) (Pluto TV) and [TrueID](https://tv.trueid.net): programme listings, fetched by the app
- [Open-Meteo](https://open-meteo.com): weather and air quality (CC BY 4.0)
- [USGS](https://earthquake.usgs.gov/earthquakes/feed/) earthquakes, [GDACS](https://www.gdacs.org) disaster alerts, [NASA EONET](https://eonet.gsfc.nasa.gov) natural events, [wheretheiss.at](https://wheretheiss.at) ISS position, [Polymarket](https://polymarket.com) odds: fetched by the app
- [Natural Earth](https://www.naturalearthdata.com): land and borders (public domain), in `res/raw/*.bin`
- [ESPN](https://www.espn.com) public scoreboards (sports), [RainViewer](https://www.rainviewer.com) radar tiles: fetched by the app
- World Monitor's webcam list, turned into `WmCams.java` at build time (not part of this repository)
- [radio-browser.info](https://www.radio-browser.info): radio station directory

Built with [Claude Code](https://claude.com/claude-code).

## License

[MIT](LICENSE)
