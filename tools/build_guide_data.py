"""Builds Glow's nightly data from iptv-org's open database (run by .github/workflows/guide.yml).

    <out>/glow.channels.xml  the channels Glow lists that iptv-org/epg can fetch listings for, one source each
                             (input for `npm run grab` in iptv-org/epg)
    <out>/facts.tsv          per stream id (tvg-id): country, languages, categories, closed, replaced by, adult,
                             blocked; so Glow can rank and hide channels from facts instead of guessing from names

Usage: python3 tools/build_guide_data.py <out-dir>
       python3 tools/build_guide_data.py --retry <grabbed guide.xml> <out-dir>
         (second pass: channels that got no programmes, each with its next source -> <out>/glow.channels.2.xml)
Only the box's own lists are covered (the same iptv-org playlists Glow downloads), and Pluto TV channels are
skipped (Glow already has Pluto's own schedules).
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


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "glow-tv data build", "Accept-Encoding": "gzip"})
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read()
        return gzip.decompress(data) if data[:2] == b"\x1f\x8b" else data


def main(out):
    out = pathlib.Path(out)
    out.mkdir(parents=True, exist_ok=True)
    ids, pluto = set(), set()
    for p in PLAYLISTS:
        text = get("https://iptv-org.github.io/iptv/" + p).decode("utf-8", "replace")
        for head, url in re.findall(r'(#EXTINF[^\n]*)\n(?:#[^\n]*\n)*([^\n#][^\n]*)', text):
            m = re.search(r'tvg-id="([^"]+)"', head)
            if not m:
                continue
            ids.add(m.group(1))
            if "jmp2.uk/plu-" in url:
                pluto.add(m.group(1))

    channels = {c["id"]: c for c in json.loads(get(API + "channels.json"))}
    feeds = {(f["channel"], f["id"]): f for f in json.loads(get(API + "feeds.json"))}
    blocked = {b["channel"]: b.get("reason", "") for b in json.loads(get(API + "blocklist.json"))}
    guides = collections.defaultdict(list)
    for g in json.loads(get(API + "guides.json")):
        guides[(g["channel"], g.get("feed") or "")].append(g)

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
    for sid in sorted(ids - pluto):
        base, _, feed = sid.partition("@")
        if base not in channels or channels[base].get("closed") or base in blocked:
            continue
        options = guides.get((base, feed)) or guides.get((base, "")) or []
        if not options:
            continue
        options.sort(key=lambda g: PREFERRED.index(g["site"]) if g["site"] in PREFERRED else len(PREFERRED))
        picked.append((sid, options[0]))
        alternatives[sid] = options[1:4]
    write_channels(out / "glow.channels.xml", picked)
    (out / "alternatives.json").write_text(json.dumps(alternatives))
    sites = collections.Counter(g["site"] for _, g in picked)
    print(f"{len(ids)} stream ids, {len(lines)} with facts, {len(pluto)} Pluto skipped, {len(picked)} to grab")
    print("sources:", ", ".join(f"{s} {n}" for s, n in sites.most_common()))


def write_channels(path, picked):
    rows = ['<?xml version="1.0" encoding="UTF-8"?>', "<channels>"]
    for sid, g in picked:
        rows.append("  <channel site=%s lang=%s xmltv_id=%s site_id=%s>%s</channel>" % (
            quoteattr(g["site"]), quoteattr(g.get("lang") or "en"), quoteattr(sid), quoteattr(g["site_id"]),
            escape(g.get("site_name") or sid)))
    rows.append("</channels>")
    pathlib.Path(path).write_text("\n".join(rows) + "\n")


def retry(grabbed, out):
    """Channels the first pass got nothing for, each with its next source (a site may block GitHub's servers)."""
    out = pathlib.Path(out)
    got = set(re.findall(r'<programme[^>]*channel="([^"]+)"', open(grabbed, encoding="utf-8", errors="replace").read())) \
        if pathlib.Path(grabbed).exists() else set()
    alternatives = json.loads((out / "alternatives.json").read_text())
    failed = [sid for sid in alternatives if sid not in got]
    second = [(sid, alternatives[sid][0]) for sid in failed if alternatives[sid]]
    write_channels(out / "glow.channels.2.xml", second)
    print(f"first pass: {len(got)} channels with listings, {len(failed)} without; retrying {len(second)} with another source")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--retry":
        retry(sys.argv[2], sys.argv[3])
    else:
        main(sys.argv[1] if len(sys.argv) > 1 else "out")
