# Glow roadmap

Everything Glow could still do, roughly in the order it's worth doing. Each item gets measured on the box before and
after (see [docs/BENCHMARKS.md](docs/BENCHMARKS.md)). Free and openly published sources only.

## Status at a glance (27 Oct 2026)

### Done

| Area | Feature |
|---|---|
| Box | Debloat, Glow as Home, undo scripts, developer-access shield; Gradle-free build, MIT repo, signed releases (v1.0.0 → v1.3.0, `tools/release.sh`), in-app updates (box updated itself to 1.3.0) |
| Home | Clock, weather, headline ticker, icon header (Search, World, Phone remote, Cast, IPTV, Settings); full-colour app cards; app order; smooth scrolling (29.5 % → 1.1 % late) |
| Home | Live TV row (TV Guide, favourites, recents, white/black logo cards, Resume badge); Intelligence row; Cast from phone |
| TV Guide | Numbered genre menu, ranking, fixed numbers; classic grid with logos, info panel, preview, reminders, search; Radio tab |
| Listings | epg.pw, Pluto TV, TrueID, nightly GitHub job (~1,050 channels); channel facts (closed/adult hidden, real languages) |
| Player | Fallbacks → official stream → official YouTube; ▲ 0.32 s / ▼ 0.28 s (two standby players); 1080p; last channel; typed numbers; audio + subtitles; score banners; sleep timer |
| Player menu (hold OK) | Pause, back 10 s, back to live, picture size, night sound, stats, corner picture, sleep timer |
| Remote & alerts | Remote buttons (press/hold jobs), Quick settings panel, score banners over other apps (overlay allowed with owner's OK) |
| Family & language | Kids mode (picked apps + Kids channels, PIN with lockout, everything else closed); Thai menus (Settings ▸ Language), one translation file |
| Intelligence | Overview page; world map (quakes, alerts, EONET, news hotspots, ISS, day/night, rain, 2×/4× zoom); live cams + health; weather + air quality; rain radar; predictions; Markets (BTC, ETH, gold, baht/pound/euro); Sports now (24 leagues incl. tennis + golf, favourite teams); F1 timing sheet |
| Other | World news with pictures + QR; phone remote (+ photos to screensaver, backup/restore); screensaver; Settings; My IPTV; Extra apps (SmartTube, Jellyfin, Kodi, NewPipe) |

### To do

| Area | Feature | When |
|---|---|---|
| Player | ~~▼ as instant as ▲~~ ✅ (2,353 → 276 ms); ~~audio + subtitle switching~~ ✅ | done |
| Player | ~~Corner picture (PiP)~~ ✅; ~~pause & rewind (stream's window)~~ ✅; ~~night sound / levelling~~ ✅; ~~picture size, stats~~ ✅; ~~dialogue boost~~ ✅; ~~longer rewind (kept on storage)~~ ✅ | done |
| TV Guide | ~~Now & Next~~ ✅; ~~Live sport now~~ ✅; smoother first pass (logos off the main thread ✅; rows still drawn on demand) | done / partly |
| Memory | ~~Listings' descriptions on disk, rows updated in place~~ ✅; Home settled (4 min) **71–80 MB, under 80 in 4 of 5**; at 100 s 73–91 MB while start-up jobs run | partly |
| TV Guide | ~~Radio favourites + album art~~ ✅; ~~smoother browsing~~ (53 → 17–22 % late frames; under 10 % not reached: ◀▶ window moves); Trending (TMDB, free key) | partly |
| Home & box | ~~Photos to the screensaver from the phone~~ ✅; ~~settings backup~~ ✅ | done |
| Home & box | ~~Remote remapping + quick settings~~ ✅; ~~banners over other apps~~ ✅ (notification access off by choice); ~~Thai menus~~ ✅; ~~parental PIN / Kids mode~~ ✅; ~~data usage + Wi-Fi~~ ✅ | done |
| Intelligence | ~~Markets~~ ✅; ~~overview page~~ ✅; ~~tennis/golf~~ ✅; ~~map zoom~~ ✅; ~~F1 timing (after each session)~~ ✅; F1 live timing ✖ (paid) | done |
| Smart home | ~~Home Assistant card, doorbell pop-ups~~ ✅ (tested with a stand-in server); AirPlay (test first) | partly |
| Privacy (owner's call) | Private DNS ad/tracker blocking; vendor-app audit | needs OK |
| Sharing | ~~Extra apps installer~~ ✅; ~~tour on the repo page~~ ✅ (GIF); Obtainium note ✅; ~~signed releases~~ ✅ (v1.0.0 → v1.3.0, signed on the owner's Mac, box updated itself each time); awesome-list PR (after v1.0.0, owner's OK) | next |
| Sharing | ~~Contributors' guide~~ ✅ (CONTRIBUTING.md); more languages (one file each) | later |

### Ruled out

| Feature | Why |
|---|---|
| Continue watching (Watch Next) | needs a system-only permission; Netflix/YouTube publish nothing on this box |
| Samsung TV Plus / Plex / Roku channels | no legal keyless channel lists any more (schedules only) |
| World Monitor AI brief, summaries, fact-checks | paid key; not worked around |
| Boxing / MotoGP scores | no ESPN feed |
| F1 live timing during sessions | OpenF1's live data is their paid tier (€9.90/month); Glow shows each session's sheet once it ends |
| Unlicensed aggregators; SmartTube built in | legal-only rule (SmartTube stays a user install) |

## Done: Intelligence, batch 1 (25 Oct 2026)

- [x] Home row **Intelligence**: World map card (live snapshot), Weather, Predictions, six one-click live cams, All live cams
- [x] **World map**: earthquakes (USGS), current disaster alerts (GDACS), natural events (NASA EONET), news hotspots
      (World Monitor stories placed by country), webcams, the ISS live, day and night; filters, ◀ ▶ through events,
      OK = live news channel for that part of the world (or the cam)
- [x] **Live cams**: 22 city and space cams from World Monitor's list, live thumbnails, one click into YouTube
- [x] **Weather**: now, feels-like, air quality (US AQI, PM2.5), sunrise/sunset, 24 hours, 7 days (Open-Meteo)
- [x] **Predictions**: Polymarket's most-traded open questions (sports and price bets left out)
- [x] Live TV cards: white or black to suit each logo

## Done: Intelligence, batch 2 (25 Oct 2026)

- [x] **Sports now** (Home card + page): UFC, F1, Premier League, Champions League, NBA, NFL from ESPN's public
      scoreboards; live first, fight and motorsport on top, your time zone, free sports channels to watch underneath
- [x] **Cam health**: ended or removed streams found from their thumbnails every 6 h; next stream used, dead cams last
- [x] **Rain radar** page: last two hours around the weather city (RainViewer), over Natural Earth 1:50m land and borders

## Done: Intelligence, batch 3 (25 Oct 2026)

- [x] **Rain on the World map** (under the pins, and a Rain filter), RainViewer zoom 2 reprojected
- [x] **Sports leagues** picker in Settings: 19 competitions (no boxing/MotoGP on ESPN)
- [x] **Score banners** over live TV: goals with scorer, kick-offs, final results; switch in Settings
- [ ] **First start after an update is slow** (8–20 s seen four times): not reproducible in five tries (1.4–2.2 s);
      likely Android's own work after an update. Re-check if it comes back.

## Done: listings, facts and quick wins (25 Oct 2026)

- [x] **Nightly listings** (GitHub Actions + iptv-org/epg, `data` branch): guide coverage 353 → 841+ channels
      (News 20 → 270), incl. Al Jazeera English, DW, France 24, CNA, Bloomberg; second pass retries empty channels
- [x] **Channel facts** from iptv-org's database: closed/replaced/adult/blocklisted channels hidden; real languages
- [x] **Favourite teams** (first in Sports now and on Home; alerts "favourites only")
- [x] **Resume** card, **Cast from phone** (box's Miracast receiver), **sleep timer** (▶ while watching)
- [ ] ~~Samsung TV Plus / Plex / Roku channel lists~~: i.mjh.nz now publishes only their schedules, and iptv-org has
      just 3 Samsung and 1 Roku stream: no legal keyless source for the channels themselves
- [ ] ~~Continue watching (Watch Next)~~: reading other apps' entries needs a system permission
      (ACCESS_ALL_EPG_DATA, signature|privileged), and on this box Netflix/YouTube publish nothing there anyway

## Next

- [ ] **Skip epg.pw for channels the nightly data covers** (saves part of the daily download)
- [ ] **Tennis and golf**: leaderboards and live matches (ESPN tournaments)
- [ ] **Instant ▼ and favourites** (second standby player), **Now & Next** list, **audio/subtitle** switching
- [ ] **Markets ticker**: indices, oil, gold, BTC, FX from free endpoints, on the Intelligence row
- [ ] **Overview screen**: one page combining the counts, the top three events, weather and two markets
- [ ] **Map detail**: zoom to a region (◀ ▶ while holding OK), country outlines, quake depth rings
- [ ] **Flights and ships** layers if a free, keyless, permitted feed exists (OpenSky has rate limits; check terms first)

## Top picks from GitHub

| Idea | Source | Why |
|---|---|---|
| Nightly guide grabbers for many more channels | [iptv-org/epg](https://github.com/iptv-org/epg) | listings for hundreds of channels that have none today |
| Samsung TV Plus, Plex, Roku free channel lists with listings | [i.mjh.nz](https://github.com/matthuisman/i.mjh.nz) | big, legal, well-run FAST lists with EPG, like Pluto today |
| Channel metadata: country, language, categories, logos | [iptv-org/api](https://github.com/iptv-org/api) | better genres and ranking than name matching |
| Continue watching on Home (Watch Next) | Android TV `TvContractCompat` | resume Netflix/YouTube/Plex from Glow's Home |
| Remote remapping and quick settings | button-mapper style key handling | Netflix/YouTube keys to Glow, picture/sound shortcuts |
| Cast from phone | Miracast / DLNA receivers | play from a phone without Chromecast |
| Notifications overlay | `NotificationListenerService` | doorbell, messages, reminders over live TV |
| App updates without Play | [Obtainium](https://github.com/ImranR98/Obtainium)-style GitHub release checks | keeps the extra apps below current |

## Extra apps (user's choice, at their own risk)

A Settings page listing open-source apps from their official GitHub releases, installed with one click by the user
(Glow shows the source and licence, downloads the signed release, Android asks to install). Nothing is installed
unless the user picks it.

- [SmartTube](https://github.com/yuliskov/SmartTube) (YouTube without ads, SponsorBlock)
- [Jellyfin for Android TV](https://github.com/jellyfin/jellyfin-androidtv) (your own media server)
- [Kodi](https://github.com/xbmc/xbmc)
- [NewPipe](https://github.com/TeamNewPipe/NewPipe)
- a music app for the TV (to choose: one with a TV layout and official releases)

## The full list

**Live TV**: more FAST lists (Samsung, Plex, Roku) · listings for every channel (iptv-org/epg) · picture-in-picture
preview on Home · record reminders to the calendar · multi-view (two channels) · per-channel audio/subtitle memory ·
"what's on now" search across all channels · catch-up for broadcasters that publish it.

**Home**: Continue watching row · notifications · wallpaper from the screensaver photos · per-user profiles ·
voice search with the remote mic (if the remote has one).

**Box**: one-tap clean-up (cache, background apps) · storage and temperature card · Wi-Fi speed test · scheduled
reboot at night · HDMI-CEC power and volume from the TV remote.

**Phone remote**: keyboard for any app, touchpad mouse, send a link to the TV, add a channel/playlist from the phone.

**Quality**: crash reporting to a local log you can share · automatic release builds (signing key needed) ·
screenshot tests for the main screens.

## Not doing

- Unlicensed stream aggregators (TVBox configs, "iptv-api"-style scrapers, live-sports source lists), even as an
  opt-in: they re-stream pay channels without permission.
- Promoting pay-TV channels that only appear as unofficial re-streams.
- World Monitor's paid API (its keyed endpoints are for subscribers); Glow uses the same underlying free sources directly.
- Anything that switches off TLS checks or gets around a sign-in.
