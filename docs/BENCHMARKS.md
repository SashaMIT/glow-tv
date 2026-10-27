# Glow benchmarks — TV98 box

Measured on the box with `glow/tools/bench.sh` (raw output in `glow/bench/`).
"Reboot" runs restart the box and let it settle 90 s first; "no-reboot" runs reuse the running system.

## Speed pass — 24 Oct 2026

What changed: regex-free M3U parser + binary channel index (`free.idx`), logo cache on storage (24 MB LRU),
full ART pre-compile on install (`tools/install.sh`), parallel link checks during channel start, 7 s per-link
timeout (was 9 s), lower initial bitrate estimate, official backup streams + YouTube live fallback for news
channels (World Monitor catalogue).

| Metric | Before | After | Result |
|---|---|---|---|
| Channel lists loaded during boot | 12,054 ms | **466 ms** | **26× faster** |
| Channel lists loaded on each Glow start | ~1,430 ms (1,401–2,581) | **~70 ms** (55–87) | **~20× faster** |
| Glow start, Android "Displayed" (no-reboot, avg of 5) | 843 ms | **761 ms** | 10% faster |
| Logo downloads per Glow start | 7–21 | **0** | all from storage |
| Logo downloads during boot | 11 | **0** | |
| Glow memory on Home (PSS) | 40.9 MB | **36.4 MB** | −11% |
| System used RAM, idle after boot | 561 MB | **542 MB** | −19 MB |
| Memory available, idle after boot | 466 MB | **479 MB** | +13 MB |
| 1-min load average after boot settle | 4.48 | **2.79** | less boot-time work |
| Glow compile state | speed-profile | **speed** | fully pre-compiled |
| Scroll smoothness, settled (janky frames) | 4.6 % | 3.2–4.5 % | no meaningful change |
| Channel start, working Thai channels | 1.9–3.3 s | 2.1–3.7 s | unchanged (set by the stream servers) |
| Channels with official backup links | 0 | **107** | new |
| Channels with an official YouTube live fallback (live now) | 0 | **47** | new — verified hand-off plays Bloomberg live |
| APK size | 775 KB | 779 KB | |

Investigated and deliberately left alone:
- **zram** is sized 5.3 GB (75 % of the fake 7 GB RAM) but holds only ~34 MB (7.6 MB compressed, already lz4) with
  ~470 MB RAM available. Changing it needs a `/vendor` edit (dm-verity); no measured benefit → not touched.
- **Allwinner log daemon** used 3–4 s CPU since boot → negligible.
- **4 of 6 Thai test channels fail** (Channel 7, Workpoint, One 31, Thairath) in both runs: their only source,
  `lb1-live-mv.v2h-cdn.com`, returns 404 at the source and iptv-org has no alternative. They now fail in ~0.3 s.
  Fix needs their official YouTube channels added (next session).

Earlier the same day: switching from the box's built-in Allwinner player to ExoPlayer took the tested set from
mostly failing (Red Bull TV, NFL Channel, Pluto/Wurl HTTPS streams) to 9/9 playing on the first link.

## Thai rescue, phone remote, faster give-up, Spotify — 24 Oct 2026 (evening)

| Metric | Before | After | Result |
|---|---|---|---|
| Big Thai channels whose only link is dead (Ch 7, Workpoint, One 31, Thairath) | 0 / 4 play | **3 / 4 play** | via each broadcaster's official YouTube live (17 Thai channels mapped; resolved on demand, cached 10 min) |
| Time to picture for those rescued channels | never | **1.2 s / 1.2 s / 3.5 s** (Ch 7 / Thairath / Workpoint) | One 31 works whenever it's live on YouTube |
| Rescuable channels hidden as "offline" | yes (hidden 12 h) | **never hidden**; full failure hides only 30 min | they show up in lists and search again |
| Search "bbc news" | ~44 remote presses | **type on phone + 2 taps** | phone remote |
| IPTV login (31-char server, user, password) | **~264 remote presses** | **type on phone + 4 taps** | phone remote |
| Moving past a link that hangs | 9.0 s | **4.0 s** | parallel probes (3 s connect / 4 s read) |
| A second hanging link | +9 s | **skipped instantly** | already probed in parallel |
| Giving up on a fully dead channel | ~27 s | **11.1 s** | measured with `tools/mock_xtream.py` "Hanging Channel" |
| Spotify running in the background | 17 MB PSS, auto-starts | **0**, disabled | user doesn't use Spotify; `undo_phase2.sh` re-enables it |
| APK size | 779 KB | 799 KB | + phone remote page, QR encoder (Nayuki, MIT) |

Phone remote: `http://<box>:8099`, 4-digit pairing code on the TV's Phone screen, home-network addresses only,
new code after 5 wrong tries, switches itself off after 30 min unused. Tested end-to-end from another device:
pair, wrong code rejected, search, play (Thai PBS on screen in 4.3 s), Home, D-pad keys, Thai-language typing,
favourites; typing from the phone no longer pops up the TV keyboard.

## TV guide, faster zapping, numbers & last channel, settings — 24 Oct 2026 (night)

| Metric | Before | After | Result |
|---|---|---|---|
| Channels with "now / next" | 0 | **164** UK/US (epg.pw, 2,021 programmes) **+ 14 Thai** (TrueID, on demand) | guide in list rows, player banner, phone status |
| Guide load on each Glow start | — | **20 ms** (90 KB index) | |
| Daily UK+US guide refresh (35 MB + 12 MB XML, streamed from gzip) | — | US: **15.8 s** in the background, **+8 MB** Java heap; Glow peak 53 MB → back to 37 MB | Java streaming parser kept — **Rust not justified** (once-a-day background job; 20 ms day-to-day) |
| Thailand list, working channels shown | 44 | **54** | channels with a YouTube backup no longer hidden |
| Zap time, same 14 channels (median) | 2,395 ms | **1,984 ms** | −17 %: neighbours' sources prepared + their servers pre-warmed |
| 20 ▲ zaps staying inside Glow | 14 played, 1 jumped out to YouTube | **20 / 20 played** | YouTube is offered (OK to open) while zapping, never forced |
| Channel by number | not possible | **type digits** (remote or phone) | 5→7 jumped to Thai PBS |
| Last channel | not possible | **Last button** (remote/phone) | back to 3HD in 1.9 s (first visit 2.6 s, warm) |
| Turning developer access (ADB) off | Android Developer options, many menus deep | **Glow ▸ Settings ▸ Developer access** (2 clicks) + red warning on Home while on | needs `pm grant … WRITE_SECURE_SETTINGS` once (done) |
| Home scroll, janky frames (3-pass) | 5.9 % | 7–10 % (noisy, no real change) | removing tile shadows didn't help; GPU draw-command time on the Mali-G31 is the limit → needs a tile redesign |
| Glow memory on Home | 36.4 MB | **42.3 MB** | guide data + remote + settings; still < 50 MB |
| APK | 799 KB | 808 KB | |

Not achieved: median zap < 1.5 s (now ~2.0 s — three HTTP round trips to each broadcaster's server dominate), and
< 1 % janky scrolling. One 31 still needs to be live on YouTube to rescue.
The 3BB GigaTV guide (37 Thai channels) was skipped on purpose: its HTTPS certificate is expired and Glow won't
switch certificate checks off.

## Classic TV Guide ("Glow TV Guide") — 24 Oct 2026 (late)

A blue, numbered satellite-style guide: menu (1 All Channels · 2 Favourites · 3 Thailand · 4 UK · 5 News · 6 Sports ·
7 US · 8 My IPTV), channel × time grid (90 min on screen, ◀▶ through the day), info panel, muted live preview,
channel numbers 101+. Opens from Home ▸ Live TV ▸ TV Guide, the remote's MENU/GUIDE key in the player (lands on the
channel you're watching), and the phone remote's "TV Guide" button. Screenshots: `shots/guide_*.png`.

| Metric | Before | After | Result |
|---|---|---|---|
| See what's on across channels | one channel at a time (row "now" text / player banner) | **7 channels × 90 min per screen**, 2,352 numbered channels | classic grid |
| Guide open, first time | — | 3,198 ms → **648 ms** | catalogue names pre-normalised once (`OfficialLive.match` was re-normalising 66 names × 3,340 channels) |
| Guide open, again | — | **101 ms** | numbered lineup kept 30 min while lists unchanged |
| Overlapping listings (e.g. a "1pm–7am" block over real shows) | info panel and highlight disagreed | **one programme at a time** (`Guide.tidy`) | info = highlighted block |
| Jump to a channel | scroll | **type 1-6-0** → Iqra Bangla | verified on box |
| OK to watch from the guide | — | **989 ms** to picture (Iqra Bangla) | MENU in player → guide on the same channel |
| UK section top rows | mostly "No information" | **full grid** | channels with guide data ordered before ones without (after top picks) |
| Browsing smoothness, same 52-key path (`tools/guidebench.sh`, 3 passes) | Free TV list: 59 % janky, p90 42–44 ms | Guide v1: 78 % janky, p90 22–29 ms → **Guide v2: 33 % janky, p90 21–34 ms** | v2: no per-cell gradient objects, shortened titles cached, single-line info text (no relayout per key) |
| Glow memory | Home fresh 42 MB | Home fresh **42 MB** (unchanged); in guide with preview 52 MB; Home after using guide 67 MB (after browsing Free TV only: 58 MB) | the extra is mostly Media3 code pages (+13 MB "Code"), which Android drops first under pressure |
| APK | 808 KB | 816 KB | |

Not achieved: < 16 ms frames on every key press (p99 still 42–61 ms — Thai text shaping on the Mali-G31/A53).
Thai schedules come from TrueID, which only lists the rest of today: after midnight Thai rows read "No information"
until the new day is published (Glow now re-checks every 5 min when a schedule is about to run out).
Home's Live TV tiles still show list positions (e.g. "57 Thai PBS") rather than the guide's numbers (105).

## Sky-style guide menu + genre ranking — 25 Oct 2026

The guide now opens on a classic menu: "glow guide" logo, tabs (TV GUIDE · SEARCH · WORLD NEWS · SETTINGS),
1 All Channels · 2 Favourites · 3 Entertainment · 4 Lifestyle & Culture · 5 Movies · 6 Sports · 7 News ·
8 Documentaries · 9 Kids · 0 More… (Music, Religious, Shopping, Recently watched, Thailand, UK, US, My IPTV, Other),
and colour keys (red Recently watched, green Search A–Z, yellow World news, blue Favourites / ★ the highlighted
channel), on the phone remote too. Ranking lives in `Genres.java`; numbers in `GuideLineup.java`.

| Metric | Before | After | Result |
|---|---|---|---|
| Guide sections | by country/list (Thailand, UK, News, Sports, US) | **Sky-style genres** + "0 More…" for countries | 7 genres from iptv-org's categories |
| Channel numbers | one running count; a section read 1119, 209, 1120, 1121… | **blocks**: Entertainment 1xx, Lifestyle 2xx, Movies 3xx, Sports 4xx, News 5xx, Docs 6xx, Kids 7xx, Music 8xx, other 9xx (overflow 1100…, 4100…) | numbers ascend inside each genre |
| Sports, top 8 | Red Bull TV, FIFA+, NFL Channel, beIN XTRA, CBS Sports HQ, Golazo, DAZN Combat | **Red Bull TV, FUEL TV, DAZN Combat, Bellator MMA, PFL MMA, FightBox HD, FightBox, Swerve Combat** (then Fast&FunBox, WWE, Monster Jam, TVS Boxing…) | action & fight first, as asked |
| News, top 8 | mixed list order | **Al Jazeera English, CNA, DW English, France 24 English, NBC News NOW, ABC News Live, CBS News 24/7, Bloomberg** (BBC News/Sky News/GB News rank first whenever their links are up) | English-language global → US → UK → Asia → Thai |
| Top picks that actually play | — | **15 / 15** (8 sports + 7 news; 1.6–3.7 s; ABC News Live via its official YouTube) | |
| Pay-TV re-streams (ESPN, CNN, Fox News Channel, beIN 1–8, NBA TV…) | could sit anywhere | **never promoted** (bottom of their genre) | |
| Other-language feeds ("FIFA+ French", "Red Bull TV DE", "Avatar (Germany)") | mixed in | **after** the English/Thai ones | country in the id isn't trusted for hand-picked channels (FUEL TV has a Portuguese id, broadcasts in English) |
| Ranking 3,138 channels | — | 748 ms → 3,903 ms (regex split) → **384 ms** | word lookups instead of regular expressions |
| Guide open | 648 ms cold / 101 ms warm | **147 ms** after Home (lineup pre-built in the background), 1.1 s if Home hasn't run yet | |
| APK | 816 KB | 828 KB | |

Not done: the ranking uses no viewing figures (there's no free, legal source for them on this box); it's hand-picked
tops + language + re-stream/local/brand rules. Personal history goes to "Recently watched", not into numbers,
so numbers stay put.

## One number everywhere, search ranking, showcase — 25 Oct 2026 (later)

| Metric | Before | After | Result |
|---|---|---|---|
| Numbers on Home tiles / Free TV rows / player banner / phone remote | list positions (e.g. "57 Thai PBS", "195 Bellator MMA") | **guide numbers** everywhere (502 Al Jazeera English, 401 Red Bull TV, 404 Bellator MMA) | `GuideLineup.numberOf()` |
| Typing a number that isn't in the current list | "No channel N in this list" | **switches to the full lineup** and plays it (in News, 4-0-1 → Red Bull TV in 4.0 s; Last → back to Al Jazeera) | like a satellite box |
| Numbers after a restart / list refresh | recomputed each time | **saved** (`numbers.tsv`); a channel keeps its number while in the same genre; new ones take the next free slot | identical file (md5) after force-stop + restart |
| Time allowed between typed digits | 1.5 s player / 1.3 s guide | **2.0 s** both | the recorded tour typed "5" then timed out ("No channel 5"); 5-0-2 now lands on Al Jazeera |
| Search "sport", first results | Sport 1 (Ukraine), Sport 1 Baltic, Sport 2 (Ukraine)… | **SportsGrid, beIN SPORTS XTRA, CBS Sports HQ, Golazo, NBC Sports NOW, T Sports 7** | guide rank first, then starts-with |
| Search opened from the phone | could flash "No working channels match" before the lists loaded | shows "Loading channels…" until they're in | |
| APK | 828 KB | 828 KB | |

Showcase: `glow/tools/tour.sh` (19 screenshots → `shots/tour/`), `glow/tools/tourvideo.sh` (2½-min screen recording,
5.2 MB H.264 in `share/glow_tour.mp4`), page in `share/index.html`.

## Final check before sharing — 25 Oct 2026

Full screenshot tour (19 screens) + guide colour keys, favourites, Guide key from the player, Last channel,
phone-remote pairing and colour buttons, on the box with logcat running.

| Check | Result |
|---|---|
| Crashes / "app not responding" | **none** (same process throughout) |
| Phone remote while switched off | port closed |
| Phone remote, unpaired request | **401**; wrong pairing code rejected |
| Guide key in the player | opens the guide on the channel being watched |
| Red / green / yellow keys | Recently watched / Search / World news |
| **Blue key** | **bug found and fixed**: this box's firmware also opens its file manager on a real blue key, which covered the guide. The phone remote's colour buttons are now handled inside Glow (no key injected); blue from Home opens Favourites, blue in the grid stars the channel and Favourites updates at once |
| Glow memory on Home after the whole session | 60 MB (42 MB fresh) |

Promo video re-cut: 28 s, starts on Home (title card moved to the end), Thai-listings shot dropped, captions
ease in once per caption instead of re-sliding on every clip.

## Listings for free channels, logos, World with pictures, icon header — 25 Oct 2026 (night)

| Metric | Before | After | Result |
|---|---|---|---|
| Channels with programme listings | 121 / 2,339 (5 %) | **348 / 2,339 (15 %)** | + Pluto TV's own schedules (i.mjh.nz), matched exactly on the Pluto id in the stream link; refreshed every 6 h (1.5 MB, 4.9 s in the background, +4 MB heap briefly) |
| … Entertainment | 28 / 561 | **162 / 561** | |
| … Movies | 6 / 109 | **55 / 109** | |
| … Documentaries / Kids | 5 / 113 · 2 / 78 | **23 / 113 · 22 / 78** | |
| Rows without any published schedule | "No information · OK to watch" | **"Live now · Comedy · OK to watch"** (the channel's genre) | rolling news / sport loops don't publish schedules |
| Guide branding | names only | **logo in every channel row + large logo in the info panel** | |
| Guide browsing (`guidebench.sh guide`, 3 passes) | 33 % janky, p90 21–34 ms, p99 42–61 ms (rows mostly one "No information" bar) | 80 % janky, p90 21–28 ms, **p99 26–42 ms** (logos + up to 4 programme blocks per row) | slowest frames better; more frames just over 16 ms → row caching is the next step |
| Netflix on the apps shelf | small icon + name on glass | **full black card with the Netflix logo** (adaptive icon background fills the card) | same treatment for any app without a TV banner |
| Header | 5 text pills + a red warning line | **6 icon buttons**; name shows under the focused one; developer access = red shield button that switches it off | |
| Favourites | empty | **14 starter channels** (Al Jazeera English, CNA, DW, France 24, NBC News NOW, ABC News Live, Red Bull TV, FUEL TV, DAZN Combat, Bellator MMA, PFL MMA, 3HD, Thai PBS, Channel 7) — once, only working ones | |
| World news | text list | **pictures** (article preview images; designed source cards where the feed only gives Google News links), detail panel with QR code to read on the phone, **OK = watch live** on a matching news channel (Middle East story → Al Jazeera English playing in 2.8 s) | |
| HTML codes in summaries ("&nbsp;") | shown | decoded | |
| APK | 828 KB | 836 KB | |

## Guide v3: cached rows, aligned logos, programme summaries, reminders — 25 Oct 2026 (late morning)

| Metric | Before | After | Result |
|---|---|---|---|
| Guide browsing, 20▼ + 20▲ on All Channels (gfxinfo, 3 runs) | 68 % janky, p90 26 ms, p99 73 ms; "slow issue draw commands" 37 of 87 frames | **14–15 % janky, p90 16–17 ms, p99 24 ms** once rows are cached (first run while building the cache: 39 %, p99 38 ms); slow draw commands 1–3 | unselected rows are drawn once into a picture (18 kept, ~200 KB each) and reused; only the highlighted row is drawn live |
| Logos in guide rows | on a shaded plate, sitting high (files pad unevenly) | **no plate; transparent border trimmed; one size; centred on the channel-name line** | same trim for the big logo in the info panel, which is centred on its text block |
| Info panel | title, channel, next | **+ one-line programme summary** (XMLTV `<desc>`, first 160 chars; e.g. Drunk History: "A lawyer defends the lives of rats in court…") | index format v2; v1 files still load and refresh themselves |
| Reminders | — | **OK on a later programme sets a reminder** (bell in the grid, "⏰ Reminder set · OK to clear" in the panel); a minute before, a dialog "Starting now … OK to watch" over any Glow screen, or a notice over other apps | tested: fired on time, OK → Comedy Central Pluto TV playing |
| Home Live TV tiles | logo + name | + **"▶ now playing"** line for channels with listings | |
| Daily US listings parse (background) | 15.8 s | 38 s this run (now also reads descriptions; network may vary) | once a day in the background; watch this |
| APK | 836 KB | 840 KB | |

## Faster listings refresh — 25 Oct 2026 (midday)

epg.pw's US file is 30 MB compressed and **202 MB** of XML for 5,493 channels; Glow uses ~120 of them. The XML pull
parser tokenised all of it. `XmltvScan` now finds each `<programme`, checks its channel id, and jumps past the block
when it isn't ours; only matching programmes are decoded (times parsed without SimpleDateFormat).

| Feed (box idle) | Before (XmlPullParser) | After (XmltvScan) | Same result? |
|---|---|---|---|
| US (epg.pw) | 38.1 s | **12.6 s** | 3,721 vs 3,720 programmes, 120 channels |
| UK (epg.pw) | 4.5 s | **1.8 s** | 3,150 vs 3,152, 95 channels |
| Pluto TV US | 4.3 s | **1.5 s** | same channels |
| Pluto TV UK | 1.9 s | **0.9 s** | same channels |
| **Whole refresh** | **~49 s** | **~17 s** | |

What's left of the US time is mostly downloading and unzipping 30 MB. epg.pw also serves one channel at a time
(`/api/epg.json?channel_id=…`, ~35 KB for 3 days): ~215 small requests would be ~7.5 MB a day instead of 42 MB.

## Lighter listings, memory, priority link checks, screensaver, Settings v2 — 25 Oct 2026 (afternoon)

| Metric | Before | After | Result |
|---|---|---|---|
| Daily UK/US listings download | ~42 MB (two whole-country files) | **2.6 MB** (215 channels from epg.pw's per-channel API, 4 at a time, 0 failed, 20 s mostly waiting on the network) | the full files are read weekly to learn which channels are ours (epgpw-map.tsv); falls back to them if a third of requests fail |
| Glow on Home after guide + preview + watching + World | 83 MB (Java 19, native 37) | **60 MB** after it settles | pictures dropped from memory when you come back Home or another app is in front (onTrimMemory) |
| Glow while Netflix is open | — | 49 MB (from 55 on Home) | memory handed to the app being watched |
| Glow fresh on Home (settled) | 42 MB (first build) / 56 MB (before this) | **37 MB** | |
| Link checks for the channels that matter | every 12 h with all 3,340 | **every 3 h** for favourites, recently watched and each genre's top 30 (272 links in 51 s); full sweep still 12 h | a dead top channel hides, a recovered one returns, the same day |
| Screensaver | box's web screensaver, never started (timeout "never") | **Glow ambient after 15 min idle**: clock, weather, world stories with pictures, "on now" for favourites, burn-in drift | Glow ▸ Settings switch; off restores the box's own values; any key wakes |
| Settings | developer access, offline channels, phone remote, refresh | **+ screensaver, weather city (17), clock 12/24/auto, Thai channels first, apps on Home (hide, reorder)** | all tested on the box, then reset to defaults |
| APK | 840 KB | 852 KB | |

## Picture quality, news → channel matching, releases — 25 Oct 2026 (late afternoon)

**Why channels looked soft.** The box sends **3840×2160 @ 60 Hz** to the monitor (`/sys/class/disp/disp/attr/sys`)
and video has its own hardware layer, but Android reports the screen as 1280×720 (only the menus are drawn at that
size). ExoPlayer caps video to the reported screen size by default, so 1080p channels were held at 720p or below.
The cap is now cleared (up to 1080p), ABR steps up after 4 s instead of 10 s and uses 80 % of measured bandwidth,
and each channel starts from the connection speed measured last time (box measured ~42 Mbps).

`tools/qualitybench.sh 6 8 25` (Sports, 8 channels, 25 s each; "offers" = best variant in the stream):

| Channel (offers) | Before: first → after 25 s | After: first → after 25 s |
|---|---|---|
| Red Bull TV (1080p) | 640×360 → 1024×576 (+21 s) | 640×360 → **1920×1080, 6.3 Mbps (+14 s)** (first channel of the run, no speed known yet) |
| FUEL TV (1080p) | 1280×720 → 1280×720 | **1920×1080, 4.7 Mbps from the first frame** |
| Swerve Combat (1080p) | 1280×720 → 1280×720 | **1920×1080, 4.9 Mbps from the first frame** |
| FightBox / FightBox HD / Fast&FunBox (single 1080p) | 1920×1080 | 1920×1080 |
| DAZN Combat (sizes not in playlist) | 1216×684 | 1216×684 (its top variant) |
| First picture | 2.3–5.0 s | 2.2–3.4 s |

**News → channel.** "Watch live" from a World story now picks: the outlet's own channel (BBC → BBC News, Bloomberg/FT/WSJ
→ Bloomberg TV, CBS → CBS News 24/7, Al Jazeera, France 24, DW, NHK, CNA, Reuters…), else the place named most in the
headline (Japan → NHK World, Korea → Arirang, India/Pakistan → WION, Thailand → Thai PBS, Israel/Gaza/Iran/Saudi → Al
Jazeera English, Ukraine/Russia → DW, France → France 24, UK → BBC News/Sky News, White House/Congress → ABC/NBC/CBS…),
else the story's region. Reviewed against all 50 top stories on the box; e.g. "Pakistan ratifies Mecca pact as Saudi
Arabia … Israel …" now goes to Al Jazeera English (2 Middle East places vs 1 South Asia), FT stories to Bloomberg TV.

**Releases.** `.github/workflows/release.yml` builds a signed APK for each `v*` tag and attaches it to a GitHub
release; Glow checks GitHub weekly and offers the update in Settings (download, then Android's own install prompt).

**Slow-starting channels no longer marked offline.** Pluto TV's ad-stitched streams take 2.3–8 s for the first picture
(measured 4× each way on Bellator MMA, with and without the remembered connection speed: server-side variation, not
the quality change). The player gave each link 7 s, so a slow start counted as a dead link and hid the channel. Once a
stream has answered with its renditions it now gets 8 s more: Bellator MMA played 3 of 3 (7.2–8.2 s).

**Radio.** Home ▸ Radio: 11 sections from radio-browser.info (Popular in Thailand, Top worldwide, UK, US, News & talk,
Chill, Jazz, Classical, Dance, Rock, Thai), up to 80 stations each with logos (initials when there's none), song
titles from the stream, screen dims after 30 s with clock and station. Tested: Cool Fahrenheit (Thailand) with its
stream title, BBC Radio 5 Live; dim screen and wake on any key.

## Home smoothness, everything in the TV Guide — 25 Oct 2026 (evening)

| Metric | Before | After | Result |
|---|---|---|---|
| Home scrolling (`tools/scrollbench.sh`, 3 passes × 3 runs) | 29.5 % late frames, p99 23–26 ms, 121 slow GPU draw passes | **1.0–1.8 % late, p99 15–19 ms** | each tile's card is a cached GPU layer (one texture per tile instead of glass + clip + contents every frame) |
| Glow on Home, settled (same build ± layers, 90–150 s after start) | 44 MB | 60 MB | the layers cost ~16 MB while Home is on screen; released in onPause, so apps like Netflix get it back |
| Tried and dropped | layers only while a row moves (released after 0.6 s / 8 s / 30 s) | 76 % / 13 % / 17 % late | rebuilding textures on the move costs more than it saves |
| Ways into live TV | TV Guide, Free TV, Radio, My IPTV tiles | **one: TV Guide** (TV and radio) | Free TV/My IPTV channels are all in the guide (country lists and My IPTV under 0 More…) |
| Radio | its own screen | **RADIO tab in the guide**: same numbered menu (10 sections, keys 1–9, 0), stations as guide rows numbered 0101…, logo + live song title in the info panel, keeps playing while you browse the guide, dims after 1 min | tested: News & talk → CNN on air, dim screen, back to the radio menu with "♫ CNN" |

## Channel changes in a fifth of a second — 25 Oct 2026 (evening)

Timeline logging (`setprop log.tag.GlowZap DEBUG`) showed where a change goes: Al Jazeera's server takes 0.5–0.9 s for
each of three playlist requests (~2.5 s before any video), DW's CDN 2.5 s for its first chunk; CNA and France 24 play
in 0.8–0.9 s. Starting each channel capped at 2.5 Mbps and playing after 1 s of buffer (was 1.5 s) made no measurable
difference (2.45 → 2.54 s), so the cap stays only as a smaller first chunk.

What works: a second, silent ExoPlayer prepares the channel above (▲) as soon as the current one shows a picture
(playlists + first seconds, 4 s buffer at most). ▲ swaps it onto the screen; if it's older than 15 s it jumps to live first.

| `tools/zapbench.sh` (TV Guide ▸ News, 20 × ▲) | Before | After |
|---|---|---|
| Median channel change | 2,454 ms | **221 ms** |
| p75 / max | 2,840 / 8,900 ms | 242 / 3,448 ms |
| Played / failed | 21 / 0 | 21 / 0 (20 of 20 ▲ used the prepared player) |
| Glow memory while watching | — | ~98 MB with the standby loaded |
| CPU while watching | — | Glow 32 %, decoder 16 %; 77 °C (idle ~70 °C) |

Trade-off: a channel reached through the prepared player can start up to 15 s behind live.

## Intelligence: world map, live cams, weather, predictions — 25 Oct 2026 (night)

A new Home row and screen built only on free, keyless sources: USGS earthquakes, GDACS alerts (current orange/red
only; its list reaches back months), NASA EONET natural events, World Monitor stories placed on the map by country,
Open-Meteo weather and air quality, Polymarket odds, wheretheiss.at, and Natural Earth land (public domain, 20 KB).
The map is drawn by Glow (no map tiles or WebView).

| Metric | Result | Notes |
|---|---|---|
| World map ready (OK on the Home card → pins drawn) | **795 ms** first time, **139–140 ms** after | parsed events kept for 5 min |
| Browsing the map (25 × ▶, 5 filter changes) | **0–3 % late frames**, p99 17 ms | |
| Live cams grid scrolling, first try (recycling GridView, layered cards) | 16–26 % late, p99 31–40 ms | layers redrawn on every reuse |
| … without layers | 93–96 % late | |
| … **all 22 cards built once in a scroll view** | **1.0–3.6 % late, p99 16–18 ms** | kept |
| Home scrolling with the new row (`scrollbench.sh`) | 1.84 % late (was 1.0–1.8 %) | Intelligence row itself 1.0–1.9 % |
| Glow on Home, settled | 68 MB → 77–79 MB | the row: six cam pictures, map snapshot, weather and odds |
| Cam pictures | 480×360 letterboxed (~690 KB each) → **320×180 16:9 (~225 KB)** | `mqdefault_live.jpg`; refreshed every 30 min instead of cached forever |
| Home + Intelligence open, settled after GC | 77–82 MB | one activity left after Back (no leak) |
| Data per refresh | ~420 KB | USGS 22 KB, GDACS 124 KB, EONET 127 KB, Polymarket 140 KB, Open-Meteo ~10 KB; cached 10–60 min |

Fixed on the way: after Back, Home's rebuilt rows sent focus to the first app (Netflix); Home now returns to the card
you left. The Intelligence menu jumped to Predictions when you came back from the map (a ListView picks the row
nearest the panel's middle); it now stays on the section you were in. Live TV cards are white, or black for logos
with white lettering (Bellator, FUEL TV), and keep that colour when focused.

## Intelligence batch 2: Sports now, cam health, rain radar — 25 Oct 2026 (night)

**Sports now** (ESPN's public scoreboards, no key): UFC, F1, Premier League, Champions League, NBA, NFL. Live first,
then league by league with fight and motorsport on top; fixtures in the box's time, results from the last 30 hours,
at most six per league (team games within 48 h). US broadcasters shown; OK plays a game only if a free channel in the
guide carries it (never a pay re-stream), otherwise focus moves to the four top free sports channels underneath.

| Metric | Before | After |
|---|---|---|
| Six scoreboards, first load | 3.3–3.7 s one after another | **0.6–1.2 s** in parallel (3 at a time); 0.2 s from cache |
| Data | — | ~62 KB gzipped per refresh; every 3 min for a league with something on or about to start, else 30 min |
| Team logos | 500 px PNG, ~80 KB each | ESPN's resizer, 80 px, **~7 KB** |
| Sports list scrolling | ListView: 8–42 % late frames (p99 up to 42 ms); with row layers 83–100 % | **rows built once in a scroll view: 0–1.1 % late, p99 15–16 ms** |

**Cam health**: every 6 h Glow downloads each cam's current thumbnail (22 × ~15 KB ≈ 330 KB, ~10 s in the
background). No picture (404) or YouTube's grey placeholder = ended; from the second round, a picture identical to the
last look (≥ 1 h earlier) = ended. Ended streams are skipped for a day and the cam's next stream is used; a cam with
none left goes to the end of the grid, dimmed, marked OFFLINE, and leaves the map and Home. First round on the box:
Medina (404) and Miami's first stream (placeholder) caught; Miami moved to its live second stream. Watch pages would
have been exact but are 1.2 MB each (17 MB a round), so they weren't used.

**Rain radar**: RainViewer's free tiles (the free service now sends one colour scheme whatever is asked), the last
two hours in five 30-minute steps, looping, over land and borders drawn from Natural Earth 1:50m
(`land_50m.bin` 247 KB, `borders_50m.bin` 81 KB; APK 951 KB → 1.23 MB). The map is laid out at web-map zoom 6; radar is
fetched at zoom 5 and drawn twice the size: 9 tiles per frame instead of ~20.

| Metric | Result |
|---|---|
| Five frames, one tile at a time | 8.1 s |
| Four tiles at a time, newest frame first and shown at once | **newest frame instantly from cache; 4 new frames in 2.2 s**; 0.1 s fully cached |
| Data | ~50 KB per frame; tiles never change, so they're kept on disk |
| Animation | 0 % late frames |
| Memory while open | ~3 MB (five half-size frames + an RGB_565 base), released when you leave the page |

**Whole app** after this batch: Home scrolling 1.07 % late frames (p99 15–19 ms); Glow on Home settled 80 MB;
cold start 2.2–2.4 s. The first start after installing an update took 8–20 s on several installs (batch 1's build too)
while later cold starts were 2.2–2.5 s: noted on the roadmap to investigate.

## Intelligence batch 3: world rain, league picker, score banners — 25 Oct 2026 (night)

**Slow first start after an update** (8–20 s seen four times last batch): tried five times this batch: right after an
install, after a 60 s wait, and with YouTube open: **1.4–2.2 s every time**. Not reproducible; those slow starts
coincided with Play services and the Play Store working after the update. Left open on the roadmap with this note.

**Rain on the World map**: RainViewer's latest radar at web-map zoom 2 (16 tiles), redrawn row by row into the map's
flat projection, under the pins; a new **Rain** filter (▲ ▼) shows it on its own.

| Metric | Before | After |
|---|---|---|
| World rain, new radar frame | 4.4 s (one tile at a time) | **2.5 s** (four at a time, shared pool with the Rain radar page) |
| Filter change (▼) on the map | ~50 ms a press, 40–44 % late frames (all 7 chips rebuilt; rain view re-baked) | **0–22 %, mostly 0, p99 12–13 ms** (chips made once and recoloured; both map variants baked) |
| ◀ ▶ browsing, rain on vs off (same build, 3–4 passes of 25) | off: 3.6–10.7 % late | on: 3.6–20.7 % late: rain isn't the cost; the occasional slow frame is from before (batch 1's 0–3 % was a small sample) |
| Map memory | — | +2 × 0.85 MB (RGB_565 baked map) + 1.7 MB rain picture |

**Sports leagues** (Glow ▸ Settings ▸ Sports leagues, tick boxes): 19 competitions: UFC, PFL, F1, IndyCar, NASCAR,
Premier League, Champions League, Europa League, La Liga, Serie A, Bundesliga, Ligue 1, MLS, NBA, WNBA, NFL, college
football, NHL, MLB. On by default: UFC, PFL, F1, Premier League, Champions League, NBA, NFL. ESPN has no boxing or MotoGP
scoreboards (HTTP 400). Tested on the box: ticking La Liga saved it and Sports now listed it; then reset to defaults.

**Score banners over live TV** (Glow ▸ Settings ▸ Score alerts, on by default): a dark banner top-right for 8 s
for a football goal (with the scorer and minute from ESPN's scoring plays), a kick-off, or a final result in the
chosen leagues; NBA/NFL score changes are left out. Checked every minute while TV plays (scores refresh every 3 min
when a league is live). `setprop log.tag.GlowAlertTest DEBUG` shows a sample banner (used for the screenshot, then
switched back). First version used the glass style and was hard to read over bright studio pictures; now solid.

| While playing Al Jazeera 2.5 min | Before (batch "zap") | After |
|---|---|---|
| Glow memory | ~98 MB | 108 MB |
| CPU (single `top` sample) | Glow 32 %, decoder 16 % | Glow 40 %, decoder 17 % |
| Temperature | 77 °C | 72.6 °C |

## Listings for ~1,000 channels, channel facts, favourite teams, Resume, Cast, sleep timer — 25 Oct 2026 (night)

**Nightly listings** (`.github/workflows/guide.yml`, 01:30 UTC): iptv-org's database picks a listing source for each
channel Glow lists (broadcaster's own site first, then large guide sites), iptv-org/epg's grabbers fetch two days on
GitHub's servers, a second pass retries empty channels with their next source, and the result is trimmed to 36 hours
and published to the `data` branch. Glow matches it on the full iptv-org id (new `Channel.feedId`).

| | Before | After |
|---|---|---|
| Channels with listings in the nightly file | — | run 1: 749 · run 2 (+ retry pass): 1,004 · run 3 (+ Pluto streams Pluto's schedules miss): **1,050** |
| Build time on GitHub | — | 7–13 min |
| Guide coverage on the box (All Channels) | 353 / 2,344 | **841 / 2,328** (News 20 → 270, Sports 25 → 107, Entertainment 166 → 240) |
| Key news channels with schedules | — | Al Jazeera English (aljazeera.com), DW English and France 24 English (tvpassport.com), CNA, Bloomberg |
| Nightly file | — | 1.5 MB gzipped (11 MB XMLTV, 43,506 programmes), read in 2.4 s, index load +0.27 s at start |
| epg.pw per-channel refresh | 215 channels | 109 (106 skipped: nightly data has them), 1.4 MB |
| Matching | first attempt: 0 of 31,035 kept (Glow stored only the base id, e.g. `aljazeera.qa`) | full id with feed (`AlJazeera.qa@English`) |

Not possible: Samsung TV Plus / Plex / Roku channels (i.mjh.nz now publishes only their schedules; iptv-org lists 3
Samsung and 1 Roku stream). tvtv.us gave nothing from GitHub's servers (192 channels); the retry pass covers most.

**Channel facts** (facts.tsv, weekly, 117 KB): 24 channels hidden (closed, replaced, adult or blocklisted); the
ranking's English/Thai preference uses the database's languages when it has them.

**TV Guide regression found and fixed**: with real listings on most rows, browsing went to 53–73 % late frames,
"slow UI thread" 20–29 per pass. A bigger row cache (36 rows, reused bitmaps) didn't help (59–73 %). simpleperf on the
main thread: ~20 % in `GuideGridView.trim` (scanning every pixel of the info panel's 240 px logo on every key press)
plus the pixel conversions it triggers. Trimmed logos are now cached by address and size, and the trim scans in from
the edges. Slow UI-thread frames 20–29 → **0**; ▲ ▼ browsing after the first pass **11–17 %, p90 16–17 ms** (first
pass, drawing rows and fetching logos: 60 %). The full `guidebench.sh` (with ◀ ▶, which moves the time window and
redraws every row): 50 %, p90 32–40 ms.

**Favourite teams**: ESPN team lists (cached a week); ids follow a team across competitions (Arsenal 359 in the
Premier League and Champions League). Tested with Liverpool: Liverpool v Brighton first in Sports now (★) and on the
Home card; then cleared. **Score alerts**: all leagues / favourite teams only / off.

**Continue watching (Watch Next)**: not possible: `ACCESS_ALL_EPG_DATA` is signature|privileged, and on this box
Netflix and YouTube publish nothing to Watch Next (both queries empty, even as root). Replaced by a **Resume** badge on
the last channel watched. **Cast from phone**: header button opens the box's Miracast receiver ("TV98 waiting for
connection"). **Sleep timer**: ▶ while watching steps 30/60/90/120 min/off; tested end to end with
`log.tag.GlowSleepTest` (seconds instead of minutes): TV stopped and Glow's screensaver started.

| Whole app | Result |
|---|---|
| Home scrolling (`scrollbench.sh`) | 1.13 % late, p99 15–18 ms |
| Glow on Home, settled | 93 MB (listings for ~840 more channels in memory) |

## Instant TV: ▼ as fast as ▲, audio & subtitles, Now & Next, live sport now — 25 Oct 2026 (late night)

| Metric | Before | After |
|---|---|---|
| ▼ channel change (`ZAP_KEY=KEYCODE_DPAD_DOWN tools/zapbench.sh`, TV Guide ▸ News, 20 presses) | median **2,353 ms**, p75 3,107, max 5,165 (one standby, for ▲ only) | **276 ms**, p75 293, min 232 (second standby for the channel below) |
| ▲ channel change | 221 ms (one standby) | 316 ms, p75 319 (two standbys share the connection) |
| Played / failed | 21 / 0 | 21 / 0 both ways |
| Memory while watching (three players) | 98–110 MB | 91–119 MB |
| Temperature | 74 °C | 73 °C |

The second standby is prepared 2.5 s after the first, so they don't compete for the connection while the picture
starts. After a change, the standby two channels away is released and both are prepared again around the new channel.

**Audio & subtitles (◀ while watching)**: lists the channel's audio languages (only when there's more than one) and
subtitles/captions; choice remembered for every channel (Media3 preferred language), subtitles drawn by Glow from the
player's cues (no media3-ui). Scan of 700 news/sports/UK streams: many carry subtitles or captions (NBC News NOW CC,
NHK World's own stream, Al Araby in 4 languages, RTVE 24h in 5). Tested on NBC News NOW: captions on, remembered on
the next start, off again ("no subtitle preference saved").

**Now & Next** (guide tab, remembered): each row shows what's on (until when) and what's next; ◀ ▶ step between them,
OK on Next sets a reminder. Footer hint follows the mode.

**Live sport now**: Sports now's free-channel row is chosen from the listings: titles that read live ("LIVE", "x v y",
not "highlights"/"replay") first, then channels with a listing, then the rest. On the box tonight it showed FIFA+
("Live | FIFA U-17 Women's…"), NBC Sports NOW ("PFT Live") and Pluto's handball channel (EHF Champions League) next
to 5 live ESPN games (Liverpool 2–0 Brighton, 56').

**Guide**: logos now trimmed and scaled off the main thread; fixed: when the logo store emptied at 120 entries, those
logos were never asked for again. `guidebench.sh` (with ◀ ▶ window changes): grid 50 % → **43 %**, p90 by pass
36 / 31 / 24 ms; Now & Next mode **20 %**, p90 31 / 16 / 15 ms.

**Listings on the box after the third nightly run**: **1,032 / 2,326 channels** (News 343, Sports 140); index loads
in 0.42 s. **Home**: 2.2 % late frames when scrolling; settled 114 MB (93 MB before the third run's listings): the
listings held in memory are now the biggest cost → next on the roadmap.

## Memory diet — 25 Oct 2026 (late night)

Heap dump (`am dumpheap`, parsed with a small HPROF reader): Glow's live Java objects were ~20 MB, **~14 MB of it text**
(145,000 strings): mostly the listings' titles and 220-character descriptions (38,024 programmes, 3.65 M description
characters). Descriptions are only read for the highlighted programme, so programmes loaded from the saved index now
keep **where their description is in the file** and the info panel reads it on demand (one small read; a stamp
guards against the file having been rewritten). After a refresh Glow re-reads the new index, so fresh descriptions
don't stay in memory either.

| Glow on Home | Before | After |
|---|---|---|
| Java heap, settled | ~37 MB (24–39 across runs) | **14–22 MB** |
| Total, settled (3 cold starts) | 104–118 MB | **81–100 MB** (median 99) |
| After 5 returns to Home | 151 MB (native 47 → 87 MB) | 120–135 MB (native ~56–62 MB, incl. a guide visit) |
| Listings index load | 0.42 s | 0.24 s (descriptions skipped, not decoded) |
| Home scrolling | 2.2 % late | 1.6 % late |

Other changes: Intelligence/Sports data parsed once while fresh (was re-parsed on every return to Home); Home's
parse-heavy jobs run one at a time (`Net.WORK`), network refreshes stay parallel; rows are only rebuilt when what they
show changes (apps signature, same channels refreshed in place, Intelligence cards updated in place) and get their GPU
layers back on return; trimmed-logo cache 40 → 12 and cleared on return to Home.

Not reached: under 80 MB every time. What's left is mostly pictures and GPU textures (24 MB of GPU memory, half of it
the cached card layers that keep Home scrolling at ~1–2 %; dropping them was tried before and made scrolling 13–76 %
late). Next options: fewer cards with layers (e.g. hide system utilities from the Apps row by default), smaller banners.

**Guide ◀ ▶ (time window moves)**: at most one new row picture per frame; the other new rows are drawn directly until
their turn. `guidebench.sh`: p90 by pass 36 / 31 / 24 → **26 / 21 / 23 ms**, p99 53 / 46 / 38 → **44 / 34 / 34 ms**;
more (shorter) frames, so "late frames" stays ~50 %. Three pictures per frame was worse (p90 27–32 ms).

## Share-ready: Extra apps, photos and backup from the phone, GIF tour — 25 Oct 2026 (late night)

**Extra apps** (Settings): SmartTube (GitHub, armeabi-v7a), Jellyfin (GitHub release APK), Kodi (kodi.tv mirror,
newest stable armeabi-v7a: 21.3 Omega), NewPipe (GitHub). The latest release is looked up when picked; the dialog shows
version, size, source and licence; Glow downloads it and hands it to Android's installer (shared with Glow's own
updates, `Updates.installApk`). Tested with NewPipe v0.29.1 up to Android's "Do you want to install this application?"
screen, then cancelled: not installed. Kodi is detected as installed (19.3).

**Phone remote ▸ More**: photos stream to the box (up to 20 MB each, shrunk to ≤1920 px JPEG, newest 30 kept) and show
on the screensaver every other slide; settings backup/restore as JSON (apps, channels, display, dream, player, sports,
reminders; never the IPTV sign-in). Tested from a computer on the LAN: unpaired upload → 401; photo saved; non-image
rejected; backup of 90 settings with no credential keys; restore of the same file applied 90; the photo appeared on the
screensaver ("Your photos · From your phone"); test photo deleted and the phone remote switched off afterwards.

**Front page**: `docs/glow-tour.gif`, 10 frames of the newest screens, 720 px, 654 KB.

| Home (3 cold starts) | Before this chunk | After |
|---|---|---|
| Time to first frame | 2.2–2.5 s | **1.05–1.09 s** (heavy start-up jobs one at a time; descriptions not decoded) |
| Glow on Home, settled | 104–118 MB | **80–87 MB** |

**Release v1.0.0**: built and signed on the owner's Mac with `tools/release.sh 1.0.0` (key never uploaded; certificate
SHA-256 c2810927…ed6fd8, the same as the box's copy), APK published to GitHub Releases. The tag-triggered workflow
that would have attached a throwaway-key build is now a manual build check. On the box: the weekly check found
"latest 1.0.0 (installed 1)", Settings showed "Update available: Glow 1.0.0", Android offered "an update to this
existing application", and after Install the box runs versionName 1.0.0 / versionCode 10000 with its data intact.

## Player extras: Player menu (hold OK), pause & rewind, picture size, night sound, stats, corner picture — 25 Oct 2026 (late night)

Everything is in one **Player menu** (hold OK while watching; a short press still shows the info). Tested on the box
through the menu, item by item:

| Feature | Result on the box |
|---|---|
| Pause / Play | paused 8–20 s, resumed "13–25 s behind live"; "Back to live (18 s behind)" offered and works |
| Back 10 seconds | works within the channel's own live window; never earlier than 8 s into it (Al Jazeera's window is short: a 10 s rewind after a 20 s pause went past it, `BEHIND_LIVE_WINDOW`, and the player jumped to live, so now it says "That's as far back as … keeps") |
| "Behind live" | measured from each channel's own normal delay (Al Jazeera's is ~29 s), reset only when the channel changes |
| Picture size | Fit / Fill / Stretch / Zoom (×1.33, for 4:3 shows inside a 16:9 frame), remembered |
| Night sound | Android DynamicsProcessing on the player's audio session: compressor (−32 dB, 4:1, +12 dB) + limiter (−3 dB). This box refuses it before the stream's audio exists ("Cannot initialize effect engine", −3), so it's applied once the picture is up, with one retry: works |
| Stats | 1920×1080 25 fps 6128 kbps avc · buffer 24.9 s · connection 20.3 Mbps · dropped frames · behind live · night sound |
| Corner picture | Al Jazeera kept in the corner (≤960×540, ≤2.5 Mbps, muted) while ▲ changed the big picture to CNA in 1.6 s; Swap and Close work. Standbys are released while it's on (their decoder goes to the corner) |

| One vs two pictures | One (standbys on) | Two (corner on) |
|---|---|---|
| Glow memory | 115 MB | 112 MB |
| Temperature | 77.7 °C | 79.8 °C |
| CPU (one `top` sample) | — | Glow 72 %, decoders 25 % + 23 % |

Not done: 5 minutes of live TV held in memory: at 1080p that's ~190 MB on a 1 GB box; pause and rewind use each
stream's own live window instead (often 30 s–2 min). Development builds are now numbered just above the latest release
(1.0.0 → 10001 "1.0.0-dev"), so they install over a released copy.

## Remote buttons, Quick settings, banners over other apps — 26 Oct 2026

The box's IR remote maps through one of 14 `customer_ir_*.kl` files chosen by the remote's code, so Glow doesn't
guess: **Settings ▸ Remote buttons** asks you to press a spare button and choose its jobs (press and hold): an app,
TV Guide, Sports now, World map, Search, last channel, Quick settings. Buttons Glow needs (D-pad, OK, Back, Home,
numbers, volume, channel, Menu, and blue, which the firmware takes) can't be reassigned. It works while Glow is on
screen; in every app via the optional "Glow remote buttons" accessibility helper, which only reacts to the buttons set
and never reads the screen (switched on by the owner in Settings ▸ Accessibility; not switched on here).

| Test on the box | Result |
|---|---|
| Set red: press → Sports now, hold → Quick settings | saved as `press_183 = glow:sports`, `hold_183 = glow:quick` |
| Red on Home | IntelActivity (Sports now) |
| Hold red on Home | Quick settings panel over Home |
| Quick settings during playback: Night sound off | applied on return (stats overlay dropped "night sound") |
| Test mapping removed afterwards | red does nothing again |

**Banners over other apps**: with the owner's OK, `appops set com.tv98.glow SYSTEM_ALERT_WINDOW allow` (undo line added
to `box/undo.sh`). Android TV has no settings page for it, nor for notification access, so notification banners are
built but **off** (notification access not granted, by choice). Score alerts check every minute only while another app
is in front; a sample alert appeared over Android's Settings after 90 s.

| Home (3 cold starts, 100 s settle) | Before | After |
|---|---|---|
| Time to first frame | 1.05–1.09 s | 1.05–1.07 s (one 1.9 s outlier) |
| Glow on Home | 80–87 MB | 89–104 MB (median 91; the every-minute alert check now runs only when another app is in front) |

**v1.2.0 release** (signed on the Mac, cert `c2810927…ed6fd8`): Home found it on its update check, Settings showed
"Update available: Glow 1.2.0", OK → Android's installer → versionName 1.2.0 (10200). Release build, 3 cold starts with
100 s settle: 1.010 / 1.014 / 1.037 s, Glow 88.7 / 87.0 / 99.8 MB.

## Kids mode and Thai menus — 26 Oct 2026

**Thai menus.** English stays in the code; `res/raw/lang_th.tsv` (372 phrases, 66 patterns with `{n}` numbers,
`{t}` times and `{}` names) is applied where text is shown: every `Ui.text` view, toasts, dialogs and the few
canvas-drawn labels, so no screen's code had to be rewritten. Longer text is tried piece by piece between "·", ":",
wide gaps and line breaks. First version let `{}` run across "·", which turned "AIR QUALITY · Moderate" into
"อากาศQUALITY" and could have caught headlines ("Air India…"); placeholders now stop at "·" and line breaks, and
number/time slots only match digits. Checked on the box screen by screen: Home, Settings, TV Guide menu and grid,
player banner and menu, Quick settings, World map, Sports now, Weather. Channel names, programmes and headlines stay
as broadcast.

**Kids mode.** Home shows only the apps a grown-up ticked and the guide's Kids section (71 working channels: PBS
Kids, Nick Jr., Nickelodeon, Retro Toons, Animation…); no headline line, no Intelligence row, one "Grown-ups"
button. Any other Glow screen closes as it opens (checked: Search key, starting Search, Intelligence and Settings
directly; Settings asks for the PIN instead). The guide opens on Kids with no menu; typing 503 (Al Jazeera) in the
player says "No channel 503."; YouTube hand-offs are skipped; remote-button jobs other than the Kids guide, Home and
picked apps do nothing; the screensaver shows photos, clock and weather but no news. PIN: salted SHA-256, five wrong
tries then 60 s (the right PIN is refused during the wait too); the phone remote is switched off when Kids mode
starts and the PIN isn't in the phone backup.

| Home, 3 cold starts, 100 s settle | Time to first frame | Glow memory |
|---|---|---|
| v1.2.0 (before) | 1.010 / 1.014 / 1.037 s | 88.7 / 87.0 / 99.8 MB |
| English | 2.200 (first after install) / 0.977 / 0.950 s | 95.8 / 93.1 / 99.9 MB |
| Thai | 1.073 / 1.036 / 1.039 s (table load 26 ms) | 99.9 / 89.4 / 95.9 MB |
| Kids mode | 1.479 (first after install) / 0.700 / 0.673 s | 62.6 / 58.2 / 55.5 MB |

| Home scrolling (`scrollbench.sh`, 3 passes) | Late frames | p99 |
|---|---|---|
| English | 1.24 % / 1.51 % | 15–20 ms |
| Thai | 5.18 % (first run, Thai glyphs drawn for the first time) / 0.86 % | 16–23 ms |

APK 1,270,720 → 1,291,265 bytes (+20 KB, mostly the Thai table).

## Intelligence 4: Overview, Markets, F1 timing, tennis and golf, map zoom — 26 Oct 2026

- **Overview** (first in the Intelligence menu): weather, the Sports now headline, markets, the most serious
  quake/alert, the top story, the top prediction, a live cam and the last F1 podium on one screen. Each card fills
  as its source answers (all eight in 3.4 s cold, F1 last); the first version waited for all of them.
- **Markets**: CoinGecko `coins/markets` (Bitcoin, Ether, PAX Gold ≈ 1 oz of gold, 7-day lines; one call, kept 10
  min) and Frankfurter/ECB rates (USD, GBP, EUR in baht, GBP in dollars; 3 weeks, kept 6 h). Home's Intelligence
  row has a Markets card ("BTC $79,987 −0.5% / Gold $4,015 · $1 = ฿33.75").
- **F1 timing**: OpenF1's free tier is historical only; live data is their €9.90/month tier, which Glow doesn't
  use. The sheet shows the latest finished session (sessions list, `session_result`, `drivers`, `stints`; four
  calls, under their 30/minute limit; kept a week once settled). OK on the F1 row in Sports now opens it.
- **Tennis and golf** in the league picker (ATP, WTA, PGA Tour, DP World Tour, LPGA; off by default): one row per
  tournament, the singles match on now (latest round), else the next, else the latest result with set scores; golf
  shows the top three to par. Checked with a live WTA match (Guangzhou, 4-2).
- **Map zoom**: CH+ 2× then 4× on the selected pin, CH− and Back out; OK still tunes to news or the cam (the prompt
  said OK, but OK already does that on the map). Land is redrawn sharp from Natural Earth, skipping shapes out of
  view.

| Key response (`tools/intelbench.sh`, 3 passes, frame times) | Before | After |
|---|---|---|
| World map ◀▶ ×20 | p99 13–14 ms (one pass 65 ms) | p99 14–40 ms, 0–1 janky |
| World map zoomed 4×, ◀▶ ×20 (first version: redraw on the main thread) | — | p99 **150 ms**, 13 of 20 frames janky |
| World map zoomed 4×, ◀▶ ×20 (redraw off the main thread, old picture moved and scaled meanwhile) | — | **p99 12–16 ms, 0 janky**; sharp land ~97 ms later |
| Sports now ▼ ×12 | p99 16 ms | p99 20 ms (24 leagues possible, 5 new off by default) |

| Home, 3 cold starts, 100 s settle | Time to first frame | Glow memory |
|---|---|---|
| Before (Kids/Thai build) | 0.950–0.977 s | 93–100 MB |
| After, with Kodi left running in the background by a test | 1.09–1.27 s | 83–90 MB |
| After, Kodi closed | **0.963 / 0.973 / 1.003 s** | **88.0 / 91.8 / 92.2 MB** |

Kodi had been open since a Kids mode test (6 % CPU, 64 MB all the time): it, not Glow, made those starts slower.
APK 1,291,265 → 1,303,553 bytes.

## Player and radio, network, Home Assistant — 26–27 Oct 2026

**Dialogue boost** (player menu, Quick settings): DynamicsProcessing pre-EQ (≤150 Hz −4 dB, 1–4 kHz +6 dB) with a
limiter at −1 dB catching the extra level; works on its own or with night sound (compressor + limiter).

**Rewind further back.** Free streams keep 8–30 s of live window. Glow now keeps the segments the player downloads
anyway (no extra internet) in a store on storage, remembers their order from the playlists it reads, and when you go
back past the stream's window it plays a playlist built from what it kept (`Timeshift.java`, HLS only).
- Storage: this box had **366 MB free** on a 3.5 GB /data; Android warns at 5 % (~175 MB). Glow uses a USB stick if
  one is plugged in, else internal storage keeping Android's line + 100 MB free (73 MB here ≈ 1.5 min of 6 Mbit/s
  1080p, ~3 min at 3 Mbit/s), and gives it back when the player closes. A 150 MB internal store would have pushed
  the box to its warning line.
- On the box (Al Jazeera English, 1080p): 2 min live, Back 1 minute → "61 s behind live", playing from the store;
  a minute later still 66 s behind (steady); Back to live; then paused 60 s (past the stream's window) and resumed
  49 s behind with no error. First version showed "114 s behind": the formula added the stream's delay instead of
  taking off the 3 segments live playback sits behind the newest.
- Zapping (`zapbench.sh`, 20 ▲): before median 338 ms / p75 390; after **279 / 308**; with rewinding switched off
  289 / 312. Memory while playing 91 / 108 / 129 MB across those runs: it follows the channel, not the store.

**Radio**: favourites (hold OK, or blue from the phone; "★ My stations" first in the radio menu), the song's cover
from Apple's iTunes Search (no key) in the preview and on the dimmed screen, a Radio card on Home. Song titles are
cleaned before searching ("Jungle River Boat by Les Baxter - Classic Vinyl on walmradio.com" found nothing; "Les
Baxter Jungle River Boat" found the album).

**TV Guide rows drawn off the main thread.** Row pictures are now drawn on a background thread, kept per time
window (48), and the rows around the screen and the next/previous windows are drawn while the remote is idle.
Rows are opaque RGB_565 (a dark line colour between programmes), and the three full-screen screens no longer
draw the window background under their own.

| `guidebench.sh guide` (3 passes; ▲▼ and ◀▶) | Late frames (per pass) | p90 |
|---|---|---|
| Before | 53.6 % (62 / 48 / 48), 54.5 % | 25–29 ms |
| Rows drawn in the background | 26.7 %, 26.4 % (first pass 30 %) | 19–23 ms |
| + opaque rows | 19.1 % (25 / 16 / 13); after the box restarted 26–35 % | 17–30 ms |
| + no hidden window background | **21.8 %, 17.2 %** | 17–24 ms |

Frame stages (▲▼ ◀▶, framestats): GPU issue 6.0 → 4.2 ms median, whole frame 13.8 → 10.7 ms. The 10 % target
isn't reached: what's left is mostly ◀▶ moving the time window before the next window's rows are drawn.

**The box restarted itself** during a guide benchmark on 26 Oct (after hours of benchmarks at 80–82 °C), then stayed
off the network until it was unplugged and plugged back in. No crash record of any kind was left (no tombstone, no
kernel record; Glow crashing restarts Glow, not the box); its Wi-Fi driver logs impossible signal values (+9 dBm).
Benchmarks now check the temperature first (61–68 °C since).

**Network & data** (Settings): Wi-Fi band, link speed, address; speed test against Cloudflare's public test server
(**107 Mbit/s, delay 71 ms** on the box; the first version measured 117 ms because every small request opened a new
secure connection); data used today/this month from Android's counter, added up every 15 min (the counter restarts
with the box). The driver's impossible signal values show as "signal unknown". Per-app data would need usage access.

**Home Assistant** (phone remote ▸ More): address + long-lived token (checked against the server; a wrong token gets
"The token wasn't accepted"), up to 6 sensors on a Home card, a doorbell and a camera. Tested against
`tools/mock_ha.py`: Home card "Living room 23.3 °C / Front door Locked"; the doorbell pop-up with the camera picture
showed ~3 s after the ring (checked every 3 s), over Home. The token stays on the box (never sent back to the phone,
not in backups).

**Home memory, measured properly.** The usual reading 100 s after a cold start caught start-up jobs (listings,
link checks, headlines) still running: 73–91 MB, Java heap 11–20 MB. Read again at 4 minutes, the same starts were
71.0 / 72.6 / 73.1 / 78.0 / 80.3 MB (Java heap 9.6–17.3 MB): **under 80 MB in 4 of 5**. Tried and undone (no
measurable effect): a clean-up 45 s after start (74–91 MB), halving the shared picture cache (79–97 MB).

## TV Guide ◀ ▶, Home memory, Tonight — 27 Oct 2026

**Benchmark fixed first.** `guidebench.sh` used to bring an already-open guide forward, which can sit on its menu
(one run moved the menu's focus instead of the grid: 20 frames for 40 presses). It now force-stops Glow, opens the
guide fresh, waits for the section, reports ▲▼ and ◀▶ separately and prints the box's temperature (it warns above
70 °C; the box throttles when hot).

| Guide, All Channels (3 passes) | ▲▼ late frames | ◀▶ late frames |
|---|---|---|
| Before (1.4.0) | 28.7 / 23.3 / 17.8 % | 43.0 / 39.3 / 36.3 % (≈120 frames for 16 presses) |
| Shifted pictures + 3 windows drawn ahead + one redraw per batch | 28.9 / 11.5 / 13.9 %, 25.2 / 13.9 / 11.5 % | 50.8 / 44.1 / 38.3 %, 41.5 / 42.9 / 41.0 % (≈60 frames) |
| + info panel text without shadows (undone: no change) | 25.2 / 12.8 / 15.4 % | 42.9 / 43.5 / 41.3 % |

When ◀ ▶ moves the time window, each row's picture from the window next to it now slides into place until the new
one arrives (a copy, instead of drawing the row), and arriving pictures share one redraw: ◀▶ needs half the frames.
What's left per ◀▶ frame is the graphics chip (framestats: "sync" p90 6.5 ms uploading new pictures, GPU p90 11 ms);
the preview isn't playing then (each press restarts its 1.2 s wait). **Under 10 % isn't reached**; ▲▼ after the
first pass is 11–15 %.

**Home memory, 4 minutes after a cold start.** Earlier today 71.0 / 72.6 / 73.1 / 78.0 / 80.3 MB. A clean-up 150 s after
start: 74.0 / 88.4 / 74.9 / 79.8 / 82.4 MB (no better; undone). The swing is the Java heap (10–21 MB) at the moment of
reading, i.e. garbage not yet collected. **Under 80 MB every time isn't reached.**

**Tonight on free TV** (Home row + TV Guide ▸ TONIGHT). The listings have titles and times but no categories, so a
pick is judged from the channel's genre, the length and the title: films (80–200 min on film channels), live sport
("live" in the title on a sport channel; other matches as "Sport"), premieres, news specials, long documentaries.
First version labelled replays as live sport (Bellator 199 from 2018, "ODI HLs", a 2019 World Cup match): now
highlights, replays and titles naming a past year are left out. It also picked two pay channels' re-streams (Star
Sports Select, Match! Boets): the never-promote list gained Star Sports, Sony Sports, SuperSport, TSN, Sportsnet,
BT Sport, Premier Sports, Viaplay, Astro, Canal+, Movistar, Match! and Setanta, and other-language channels are left
out. Tonight's picks on the box: Tennis Today Live, a Bellator fight night, Dominique (MovieSphere), Friday the 13th
Part VIII (Pluto), The Rat Race, Indecent Proposal, The Early Line Live… Reminder set and cleared from the Home card.

| Home with the Tonight row (3 cold starts) | Before | After |
|---|---|---|
| Time to first frame (ActivityTaskManager "Displayed") | 0.96–1.0 s | **0.949 / 0.957 / 1.005 s** |
| Glow at 4 min | 71–88 MB | 80.3 / 86.1 / 81.0 MB (Java heap 20–26 MB) |
