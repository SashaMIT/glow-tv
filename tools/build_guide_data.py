"""Builds Glow's nightly data from iptv-org's open database (run by .github/workflows/guide.yml).

    <out>/glow.channels.xml  the channels Glow lists that iptv-org/epg can fetch listings for, one source each
                             (input for `npm run grab` in iptv-org/epg)
    <out>/facts.tsv          per stream id (tvg-id): country, languages, categories, closed, replaced by, adult,
                             blocked; so Glow can rank and hide channels from facts instead of guessing from names

Usage: python3 tools/build_guide_data.py <out-dir>
       python3 tools/build_guide_data.py --retry <pass> <grabbed.xml>[,<grabbed-2.xml>...] <out-dir>
         (pass 2, 3...: channels with no programmes so far, each with its next source -> <out>/glow.channels.<pass>.xml)
Only the box's own lists are covered (the same iptv-org playlists Glow downloads). Pluto TV channels are included
too: Glow prefers Pluto's own schedules, and uses these where Pluto has none (e.g. Red Bull TV).
"""
import collections
import gzip
import json
import pathlib
import re
import sys
import urllib.request
from xml.sax.saxutils import escape, quoteattr

PLAYLISTS = ["countries/th.m3u", "categories/news.m3u", "categories/sports.m3u", "countries/uk.m3u", "countries/us.m3u"]
API = "https://iptv-org.github.io/api/"
# Sources tried first when a channel has several: the broadcaster's own site, then big, steady guide sites.
PREFERRED = ["aljazeera.com", "tvtv.us", "tvpassport.com", "freeview.co.uk", "mytelly.co.uk", "sky.com", "tvguide.com",
             "i.mjh.nz", "xumo.tv", "plex.tv", "distro.tv", "mewatch.sg", "trueid.net", "3bb.co.th", "tv.trueid.net"]


# "ABC.us@KAAL": a local station's feed.
LOCAL_STATION = re.compile(r"^[KW][A-Z]{2,}")
# Channels that listing sites know by another name.
ALIASES = {"Strongman": "Strongman Champions League"}


def name_keys(name):
    """Lower-case words, so names can be compared; "UK: Action Movies - Rakuten TV" also as "rakuten tv action movies"."""
    name = re.sub(r"^[A-Za-z]{2}:\s*", "", name)
    keys = [name]
    m = re.match(r"(.+?)\s+-\s+(Rakuten TV)$", name)
    if m:
        keys = [m.group(2) + " " + m.group(1)]
    return [re.sub(r"[^a-z0-9]+", " ", k.lower()).strip() for k in keys]


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "glow-tv data build", "Accept-Encoding": "gzip"})
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read()
        return gzip.decompress(data) if data[:2] == b"\x1f\x8b" else data


def main(out):
    out = pathlib.Path(out)
    out.mkdir(parents=True, exist_ok=True)
    ids, pluto = set(), {}
    for p in PLAYLISTS:
        text = get("https://iptv-org.github.io/iptv/" + p).decode("utf-8", "replace")
        for head, url in re.findall(r'(#EXTINF[^\n]*)\n(?:#[^\n]*\n)*([^\n#][^\n]*)', text):
            m = re.search(r'tvg-id="([^"]+)"', head)
            if not m:
                continue
            ids.add(m.group(1))
            p = re.search(r"jmp2\.uk/plu-([0-9a-f]+)", url)
            if p:
                pluto[m.group(1)] = p.group(1)

    # Pluto channels that Pluto's own schedules (which Glow fetches itself) already cover don't need a second copy.
    pluto_covered = set()
    for region in ("us", "gb"):
        pluto_covered |= set(re.findall(r'<channel id="([0-9a-f]+)"', get(f"https://i.mjh.nz/PlutoTV/{region}.xml.gz").decode("utf-8", "replace")))
    skip = {sid for sid, pid in pluto.items() if pid in pluto_covered}

    channels = {c["id"]: c for c in json.loads(get(API + "channels.json"))}
    feeds = {(f["channel"], f["id"]): f for f in json.loads(get(API + "feeds.json"))}
    blocked = {b["channel"]: b.get("reason", "") for b in json.loads(get(API + "blocklist.json"))}
    guides = collections.defaultdict(list)
    feeds_of = collections.defaultdict(list)  # a channel's listing rows under any feed
    by_name = collections.defaultdict(list)  # rows iptv-org hasn't tied to a channel, by their name
    for g in json.loads(get(API + "guides.json")):
        if g.get("channel"):
            guides[(g["channel"], g.get("feed") or "")].append(g)
            feeds_of[g["channel"]].append(g)
        else:
            for key in name_keys(g.get("site_name") or ""):
                by_name[key].append(g)

    # Facts, one line per stream id Glow knows.
    lines = []
    for sid in sorted(ids):
        base, _, feed = sid.partition("@")
        c = channels.get(base)
        if not c:
            continue
        f = feeds.get((base, feed)) or {}
        lines.append("\t".join([
            sid, c.get("country") or "", ",".join(f.get("languages") or []), ",".join(c.get("categories") or []),
            c.get("closed") or "", c.get("replaced_by") or "", "1" if c.get("is_nsfw") else "", blocked.get(base, ""),
        ]))
    (out / "facts.tsv").write_text("# id\tcountry\tlanguages\tcategories\tclosed\treplaced_by\tnsfw\tblocked\n" + "\n".join(lines) + "\n")

    # One listing source per channel: the exact feed if listed, else the channel's main entry.
    picked, alternatives = [], {}
    for sid in sorted(ids - skip):  # Pluto streams too, where Pluto's own schedules don't cover them
        base, _, feed = sid.partition("@")
        if base not in channels or channels[base].get("closed") or base in blocked:
            continue
        rank = lambda g: (0 if (g.get("lang") or "en") == "en" else 1, PREFERRED.index(g["site"]) if g["site"] in PREFERRED else len(PREFERRED))
        # The exact feed's sources first, then the channel's main entry's. After those: the same channel's other feeds
        # (not for local stations of a network: their schedules differ), then rows with exactly this channel's name.
        rows = sorted(guides.get((base, feed)) or [], key=rank) + sorted(guides.get((base, "")) or [], key=rank)
        if not LOCAL_STATION.search(feed) and len({g.get("feed") for g in feeds_of[base]}) <= 8:
            rows += sorted(feeds_of[base], key=rank)
        name = channels[base].get("name") or ""
        if name in ALIASES:
            name = ALIASES[name]
        for key in name_keys(name):
            if len(key) >= 8 and " " in key:
                # A row for this channel's own region comes first ("UK: Action Movies - Rakuten TV").
                rows += sorted(by_name.get(key, []), key=lambda g: (0 if (g.get("site_name") or "").upper().startswith(feed.upper() + ":") else 1, rank(g)))
        options, seen = [], set()
        for g in rows:
            if (g["site"], g["site_id"]) not in seen:
                seen.add((g["site"], g["site_id"]))
                options.append(g)
        if not options:
            continue
        picked.append((sid, options[0]))
        alternatives[sid] = options[1:6]  # the workflow's passes try these in turn
    write_channels(out / "glow.channels.xml", picked)
    (out / "alternatives.json").write_text(json.dumps(alternatives))
    sites = collections.Counter(g["site"] for _, g in picked)
    print(f"{len(ids)} stream ids, {len(lines)} with facts, {len(pluto)} via Pluto ({len(skip)} covered by Pluto's schedules), {len(picked)} to grab")
    print("sources:", ", ".join(f"{s} {n}" for s, n in sites.most_common()))


def write_channels(path, picked):
    rows = ['<?xml version="1.0" encoding="UTF-8"?>', "<channels>"]
    for sid, g in picked:
        rows.append("  <channel site=%s lang=%s xmltv_id=%s site_id=%s>%s</channel>" % (
            quoteattr(g["site"]), quoteattr(g.get("lang") or "en"), quoteattr(sid), quoteattr(g["site_id"]),
            escape(g.get("site_name") or sid)))
    rows.append("</channels>")
    pathlib.Path(path).write_text("\n".join(rows) + "\n")


def retry(pass_no, grabbed, out):
    """Channels no pass has got anything for yet, each with its next source (a site may block GitHub's servers)."""
    out = pathlib.Path(out)
    got = set()
    for f in grabbed.split(","):
        if pathlib.Path(f).exists():
            got |= set(re.findall(r'<programme[^>]*channel="([^"]+)"', open(f, encoding="utf-8", errors="replace").read()))
    alternatives = json.loads((out / "alternatives.json").read_text())
    failed = [sid for sid in alternatives if sid not in got]
    next_up = [(sid, alternatives[sid][pass_no - 2]) for sid in failed if len(alternatives[sid]) > pass_no - 2]
    write_channels(out / f"glow.channels.{pass_no}.xml", next_up)
    print(f"before pass {pass_no}: {len(got)} channels with listings, {len(failed)} without; trying {len(next_up)} with another source")
    return next_up


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--retry":
        sys.exit(0 if retry(int(sys.argv[2]), sys.argv[3], sys.argv[4]) else 1)  # 1: nothing left to try
    else:
        main(sys.argv[1] if len(sys.argv) > 1 else "out")
