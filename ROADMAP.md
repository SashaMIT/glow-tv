# Glow roadmap

Everything Glow could still do, roughly in the order it's worth doing. Each item gets measured on the box before and
after (see [docs/BENCHMARKS.md](docs/BENCHMARKS.md)). Free and openly published sources only.

## Done: Intelligence, batch 1 (25 Oct 2026)

- [x] Home row **Intelligence**: World map card (live snapshot), Weather, Predictions, six one-click live cams, All live cams
- [x] **World map**: earthquakes (USGS), current disaster alerts (GDACS), natural events (NASA EONET), news hotspots
      (World Monitor stories placed by country), webcams, the ISS live, day and night; filters, ◀ ▶ through events,
      OK = live news channel for that part of the world (or the cam)
- [x] **Live cams**: 22 city and space cams from World Monitor's list, live thumbnails, one click into YouTube
- [x] **Weather**: now, feels-like, air quality (US AQI, PM2.5), sunrise/sunset, 24 hours, 7 days (Open-Meteo)
- [x] **Predictions**: Polymarket's most-traded open questions (sports and price bets left out)
- [x] Live TV cards: white or black to suit each logo

## Next: Intelligence, batch 2

- [ ] **Rain radar** on the Weather screen and World map ([RainViewer](https://www.rainviewer.com/api.html), free tiles, no key)
- [ ] **Sports now**: today's fixtures and live scores for the Premier League, Champions League, UFC, F1, NBA, NFL
      (ESPN's public scoreboard JSON, no key), with "watch" where a free legal channel carries it
- [ ] **Cam health**: hide cams whose stream has ended (Miami's first stream is currently a dead frame); try the
      next video id in World Monitor's list
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
