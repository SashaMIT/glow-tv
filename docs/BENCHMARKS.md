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
