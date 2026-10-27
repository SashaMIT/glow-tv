# Contributing to Glow

Glow runs on cheap Android TV boxes (the reference box: Allwinner H616, 1 GB RAM, Android 10, 32-bit). Everything
here is about keeping it fast and honest on hardware like that. Pull requests are welcome.

## Ground rules

- **Free, legal sources only.** Channels from openly published lists, official broadcaster streams and their
  official YouTube channels. No pay-TV re-streams pushed up, no piracy, no working around paid API keys.
- **Measure before and after.** Every change that could touch speed or memory gets a number on a real box, written
  up in [docs/BENCHMARKS.md](docs/BENCHMARKS.md), including what didn't work. "Feels faster" isn't a measurement.
- **Plain language on screen.** Short, specific, no jargon: "Couldn't load stations. Check the internet
  connection." rather than "Error 503".
- **Nothing irreversible on the box.** Box scripts go with an undo line in `box/undo.sh`.

## Build and install

See [README.md](README.md#build). In short:

```bash
./build.sh                                  # build/glow.apk (no Gradle; downloads pinned libraries the first time)
export GLOW_DEVICE=192.168.1.50:5555        # your box's address and ADB port
tools/install.sh                            # install and fully pre-compile
```

Logs: `adb logcat -s GlowPerf GlowPlayer`. Test switches are log tags, e.g.
`adb shell setprop log.tag.GlowZap DEBUG` (load timings while zapping), `GlowAlertTest` (a sample score banner),
`GlowSleepTest` (sleep timer in seconds), `GlowRank` (guide ranking details).

## Measure

All tools take `GLOW_DEVICE` and print one line per run. Run each two or three times; boxes are noisy, and the
first run after an install is always slower.

| Tool | What it measures |
|---|---|
| `tools/zapbench.sh <label> [n]` | Channel changes from TV Guide ▸ News: start time median/p75/max, memory while playing (`ZAP_KEY=KEYCODE_DPAD_DOWN` for ▼) |
| `tools/scrollbench.sh <label> [passes]` | Home scrolling: late frames and p99 frame time |
| `tools/guidebench.sh guide [passes]` | TV Guide browsing (▲▼ and ◀▶): late frames per pass, p90/p99 |
| `tools/intelbench.sh <label> <section> <key> [n]` | Intelligence key response (0 Overview, 1 World map, 2 Sports now…) |
| `tools/bench.sh`, `tools/qualitybench.sh` | Channel list loading; picture quality per channel |
| `adb shell am start -W …HOME` + `dumpsys meminfo com.tv98.glow` | Home cold start and memory (wait ~100 s for it to settle) |

Close any app a test opened before measuring (a Kodi left running in the background once added 150 ms to every
Home start).

## Translate

Glow's English stays in the code. Each language is one file, `res/raw/lang_<code>.tsv`: an English phrase, a tab,
then the translation, one per line. `lang_th.tsv` (Thai) is the example.

- `{n}` stands for a number, `{t}` for a time or date, `{}` for a name: `Sleep in {n} min<TAB>ปิดใน {} นาที`.
- Leave out symbols in front (★, ▶, ⏰): they're kept as they are.
- Case doesn't matter; longer text is also tried piece by piece between "·", ":" and line breaks.
- Check it on a box: Glow ▸ Settings ▸ Language, then walk Home, the TV Guide, the player menu (hold OK), Quick
  settings and Intelligence.

A new language also needs a line in `L.java` (which file to load) and an entry in Settings ▸ Language.

## Code

- Java 8, Android 10 APIs (minSdk 29), no AndroidX UI: views are built in code (`Ui.text`, glass cards, the aurora
  background). Text goes through `Ui.text` so it can be translated.
- Network and disk work off the main thread (`Net.IO` for network, `Net.WORK` for heavy jobs), results posted back
  with `Net.MAIN`.
- Lists the remote scrolls through are fixed rows in a ScrollView, or one Canvas-drawn view (the TV Guide), not
  ListViews: on a Mali-G31 a ListView re-attaching rows on every key press drops frames.
- Comments say why, not what. Match the surrounding code.

## Releases

Maintainers only: `tools/release.sh 1.2.3 notes.md` builds, signs with the release key (never committed or
uploaded) and publishes the APK on GitHub Releases; boxes offer it in Glow ▸ Settings.
