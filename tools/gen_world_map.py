"""Generates the World Map's base data from Natural Earth (public domain, naturalearthdata.com):

    res/raw/world_land.bin   land polygons, 1:110m, as int16 (lon*100, lat*100) pairs
    res/raw/land_50m.bin     land polygons, 1:50m (the rain radar's close-up), same format
    res/raw/borders_50m.bin  country borders on land, 1:50m, as lines in the same format
    res/raw/countries.tsv    ISO code, name, label latitude, label longitude, "|"-separated names Glow matches in headlines

Run once (the outputs are committed):  python3 tools/gen_world_map.py
"""
import json
import pathlib
import struct
import urllib.request

BASE = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/"
RAW = pathlib.Path(__file__).resolve().parent.parent / "res/raw"

# Extra words that point at a country in a headline (demonyms, capitals, leaders, common short forms), "|"-separated.
EXTRA = {
    "US": "u.s.|usa|america|american|americans|washington|white house|pentagon|trump",
    "GB": "uk|britain|british|england|scotland|wales|london|starmer",
    "RU": "russian|russians|moscow|kremlin|putin",
    "UA": "ukrainian|ukrainians|kyiv|kiev|zelensky",
    "IL": "israeli|israelis|jerusalem|tel aviv|netanyahu",
    "PS": "gaza|palestinian|palestinians|west bank|hamas",
    "IR": "iranian|iranians|tehran",
    "CN": "chinese|beijing|xi jinping",
    "TW": "taiwanese|taipei",
    "JP": "japanese|tokyo",
    "KR": "south korea|south korean|seoul",
    "KP": "north korea|north korean|pyongyang|kim jong un",
    "IN": "indian|delhi|new delhi|modi",
    "PK": "pakistani|islamabad",
    "FR": "french|paris|macron",
    "DE": "german|berlin|merz",
    "TR": "turkish|ankara|istanbul|erdogan",
    "SA": "saudi|riyadh",
    "SY": "syrian|damascus",
    "LB": "lebanese|beirut|hezbollah",
    "YE": "yemeni|sanaa|houthi|houthis",
    "EG": "egyptian|cairo",
    "TH": "thai|bangkok",
    "BR": "brazilian|brasilia|lula",
    "MX": "mexican",
    "VE": "venezuelan|caracas",
    "NG": "nigerian|abuja|lagos",
    "AU": "australian|canberra|sydney",
    "CA": "canadian|ottawa",
    "IT": "italian|rome",
    "ES": "spanish|madrid",
    "PL": "polish|warsaw",
}


def get(name):
    with urllib.request.urlopen(BASE + name, timeout=60) as r:
        return json.load(r)


def rings(geometry):
    t, c = geometry["type"], geometry["coordinates"]
    if t == "Polygon":
        return [c[0]]
    if t == "MultiPolygon":
        return [p[0] for p in c]
    return []


def strokes(geometry):
    t, c = geometry["type"], geometry["coordinates"]
    if t == "LineString":
        return [c]
    if t == "MultiLineString":
        return list(c)
    return []


def write(name, shapes):
    """int count; per shape: int n, then n int16 pairs (lon*100, lat*100), big-endian."""
    out = bytearray(struct.pack(">i", len(shapes)))
    for ring in shapes:
        out += struct.pack(">i", len(ring))
        for lon, lat in ring:
            out += struct.pack(">hh", round(lon * 100), round(lat * 100))
    (RAW / name).write_bytes(out)
    print(name, len(shapes), "shapes,", sum(len(r) for r in shapes), "points,", len(out), "bytes")


write("world_land.bin", [r for f in get("ne_110m_land.geojson")["features"] for r in rings(f["geometry"])])
write("land_50m.bin", [r for f in get("ne_50m_land.geojson")["features"] for r in rings(f["geometry"])])
write("borders_50m.bin", [l for f in get("ne_50m_admin_0_boundary_lines_land.geojson")["features"] for l in strokes(f["geometry"])])

countries = get("ne_110m_admin_0_countries.geojson")
lines = []
for f in countries["features"]:
    p = f["properties"]
    iso = p.get("ISO_A2_EH") or p.get("ISO_A2")
    if not iso or iso == "-99":
        continue
    names = {p.get("NAME", ""), p.get("NAME_LONG", ""), p.get("ADMIN", "")}
    words = [n.lower() for n in sorted(names) if n] + [w for w in EXTRA.get(iso, "").split("|") if w]
    lines.append("\t".join([iso, p.get("NAME", ""), "%.2f" % p["LABEL_Y"], "%.2f" % p["LABEL_X"], "|".join(dict.fromkeys(words))]))
(RAW / "countries.tsv").write_text("\n".join(sorted(lines)) + "\n", encoding="utf-8")
print(f"countries.tsv: {len(lines)} countries")
