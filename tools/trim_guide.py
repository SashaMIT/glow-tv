"""Trims an XMLTV guide to what Glow shows (1 hour back to 36 hours ahead), shortens descriptions, and packs it.

Usage: python3 tools/trim_guide.py <guide.xml> <out.xml.gz> <report.txt>
"""
import collections
import datetime as dt
import gzip
import os
import sys
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape, quoteattr

DESC_MAX = 220


def when(s):
    # "20261025143000 +0000"
    t = dt.datetime.strptime(s[:14], "%Y%m%d%H%M%S")
    off = s[15:].strip() if len(s) > 15 else "+0000"
    sign = 1 if off[0] != "-" else -1
    return t.replace(tzinfo=dt.timezone.utc) - sign * dt.timedelta(hours=int(off[1:3]), minutes=int(off[3:5]))


def main(src, dst, report):
    now = dt.datetime.now(dt.timezone.utc)
    lo, hi = now - dt.timedelta(hours=1), now + dt.timedelta(hours=36)
    per = collections.Counter()
    out = ['<?xml version="1.0" encoding="UTF-8"?>', '<tv generator-info-name="glow-tv data">']
    if os.path.exists(src):
        for _, el in ET.iterparse(src, events=("end",)):
            if el.tag == "programme":
                try:
                    start, stop = when(el.get("start")), when(el.get("stop"))
                except Exception:
                    el.clear()
                    continue
                if stop > lo and start < hi:
                    title = (el.findtext("title") or "").strip()
                    desc = (el.findtext("desc") or "").strip()
                    if len(desc) > DESC_MAX:
                        desc = desc[:DESC_MAX].rsplit(" ", 1)[0] + "…"
                    out.append("<programme start=%s stop=%s channel=%s><title>%s</title>%s</programme>" % (
                        quoteattr(el.get("start")), quoteattr(el.get("stop")), quoteattr(el.get("channel")),
                        escape(title), "<desc>%s</desc>" % escape(desc) if desc else ""))
                    per[el.get("channel")] += 1
                el.clear()
    out.append("</tv>")
    data = ("\n".join(out) + "\n").encode("utf-8")
    with gzip.open(dst, "wb", compresslevel=9) as f:
        f.write(data)
    lines = [
        f"built {now:%Y-%m-%d %H:%M} UTC",
        f"channels with listings: {len(per)}",
        f"programmes: {sum(per.values())}",
        f"size: {len(data)} bytes, {os.path.getsize(dst)} gzipped",
    ]
    open(report, "w").write("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main(*sys.argv[1:4])
