package com.tv98.glow;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Free internet radio from radio-browser.info, a community directory of public station streams (no key).
 * Each section is one query, cached on disk for 6 hours. Broken streams are left out by the directory itself.
 */
final class Radio {
    static final class Station {
        String uuid, name, url, logo, country, tags, codec;
        int bitrate, votes;
    }

    /** {label, query path}: ten, so each has a number key in the guide's radio menu (1–9, 0). */
    static final String[][] SECTIONS = {
            {"Popular in Thailand", "stations/search?countrycode=TH&order=clickcount&reverse=true"},
            {"Thai music", "stations/search?tag=thai&order=clickcount&reverse=true"},
            {"UK", "stations/search?countrycode=GB&order=clickcount&reverse=true"},
            {"US", "stations/search?countrycode=US&order=clickcount&reverse=true"},
            {"Top worldwide", "stations/search?order=clickcount&reverse=true"},
            {"News & talk", "stations/search?tag=news&language=english&order=clickcount&reverse=true"},
            {"Chill & lo-fi", "stations/search?tag=chillout&order=clickcount&reverse=true"},
            {"Jazz", "stations/search?tag=jazz&order=clickcount&reverse=true"},
            {"Classical", "stations/search?tag=classical&order=clickcount&reverse=true"},
            {"Dance & electronic", "stations/search?tag=dance&order=clickcount&reverse=true"},
    };

    /** The directory's servers; the first answer wins. */
    private static final String[] MIRRORS = {"https://de1.api.radio-browser.info/json/",
            "https://nl1.api.radio-browser.info/json/", "https://at1.api.radio-browser.info/json/",
            "https://all.api.radio-browser.info/json/"};
    private static final long FRESH_MS = 6 * 60 * 60 * 1000L;

    private Radio() {}

    /** Stations for a section: from disk if fresh, else from the directory. Call off the main thread. */
    static List<Station> section(Context c, int index) {
        File f = new File(c.getFilesDir(), "radio-" + index + ".json");
        String body = null;
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < FRESH_MS) body = read(f);
        if (body == null) {
            for (String base : MIRRORS) {
                try {
                    body = new String(Net.bytes(base + SECTIONS[index][1] + "&hidebroken=true&limit=80", 2 * 1024 * 1024), "UTF-8");
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        out.write(body.getBytes("UTF-8"));
                    }
                    break;
                } catch (Exception e) {
                    body = null;
                }
            }
            if (body == null && f.exists()) body = read(f); // stale beats nothing
        }
        return parse(body);
    }

    private static String read(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return new String(b, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Station> parse(String body) {
        List<Station> out = new ArrayList<>();
        if (body == null) return out;
        try {
            JSONArray a = new JSONArray(body);
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Station s = new Station();
                s.uuid = o.optString("stationuuid");
                s.name = o.optString("name").trim();
                s.url = o.optString("url_resolved", o.optString("url"));
                s.logo = o.optString("favicon");
                s.country = o.optString("country")
                        .replace("The United Kingdom Of Great Britain And Northern Ireland", "UK")
                        .replace("The United States Of America", "US")
                        .replace("The Russian Federation", "Russia");
                s.tags = o.optString("tags");
                s.codec = o.optString("codec");
                s.bitrate = o.optInt("bitrate");
                s.votes = o.optInt("votes");
                // One entry per station name (directories list the same station many times), and only streams
                // the player can open.
                String key = s.name.toLowerCase(java.util.Locale.ROOT);
                if (s.name.isEmpty() || s.url.isEmpty() || !seen.add(key)) continue;
                out.add(s);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---- favourites (hold OK on a station, or the blue key from the phone remote) ----

    /** My stations, in the order they were added. */
    static List<Station> favourites(Context c) {
        List<Station> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences("radio", Context.MODE_PRIVATE).getString("favourites", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Station s = new Station();
                s.uuid = o.optString("uuid");
                s.name = o.optString("name");
                s.url = o.optString("url");
                s.logo = o.optString("logo");
                s.country = o.optString("country");
                s.tags = o.optString("tags");
                s.codec = o.optString("codec");
                s.bitrate = o.optInt("bitrate");
                out.add(s);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static boolean isFavourite(Context c, Station s) {
        for (Station f : favourites(c)) if (f.uuid.equals(s.uuid)) return true;
        return false;
    }

    /** Adds or removes a station; true when it's now a favourite. */
    static boolean toggleFavourite(Context c, Station s) {
        List<Station> fav = favourites(c);
        boolean removed = false;
        for (int i = fav.size() - 1; i >= 0; i--) {
            if (fav.get(i).uuid.equals(s.uuid)) {
                fav.remove(i);
                removed = true;
            }
        }
        if (!removed) fav.add(s);
        JSONArray a = new JSONArray();
        try {
            for (Station f : fav) {
                a.put(new JSONObject().put("uuid", f.uuid).put("name", f.name).put("url", f.url).put("logo", f.logo)
                        .put("country", f.country).put("tags", f.tags).put("codec", f.codec).put("bitrate", f.bitrate));
            }
        } catch (Exception ignored) {
        }
        c.getSharedPreferences("radio", Context.MODE_PRIVATE).edit().putString("favourites", a.toString()).apply();
        return !removed;
    }

    // ---- album art for the song on air ----

    private static final java.util.Map<String, String> ART = new java.util.HashMap<>();

    /**
     * Off the main thread: cover art for "Artist - Title" from Apple's public iTunes Search (no key), or "" when
     * there's none. Station jingles and talk ("Advert", the station's own name) simply find nothing.
     */
    static String art(String song, String station) {
        song = query(song, station);
        if (song.length() < 4) return "";
        synchronized (ART) {
            if (ART.containsKey(song)) return ART.get(song);
        }
        String url = "";
        try {
            String q = java.net.URLEncoder.encode(song, "UTF-8");
            JSONObject r = new JSONObject(new String(Net.bytes("https://itunes.apple.com/search?media=music&entity=song&limit=1&term=" + q,
                    256 * 1024), "UTF-8"));
            JSONArray res = r.optJSONArray("results");
            if (res != null && res.length() > 0) url = res.getJSONObject(0).optString("artworkUrl100").replace("100x100bb", "600x600bb");
        } catch (Exception ignored) {
        }
        synchronized (ART) {
            if (ART.size() > 200) ART.clear();
            ART.put(song, url);
        }
        return url;
    }

    /**
     * What stations send, made searchable: "Jungle River Boat by Les Baxter - Classic Vinyl on walmradio.com" →
     * "Les Baxter Jungle River Boat". Bracketed bits, the station's own name and web addresses are left out.
     */
    static String query(String song, String station) {
        if (song == null) return "";
        String s = song.replaceAll("\\[[^\\]]*\\]|\\([^)]*\\)", " ");
        String st = station == null ? "" : station.toLowerCase(java.util.Locale.ROOT).trim();
        StringBuilder out = new StringBuilder();
        for (String part : s.split(" - | \\| | ~ ")) {
            String p = part.trim(), low = p.toLowerCase(java.util.Locale.ROOT);
            if (p.isEmpty() || (!st.isEmpty() && low.contains(st)) || low.matches(".*\\b[\\w-]+\\.(com|net|org|fm|co\\.uk|de|fr)\\b.*")) continue;
            int by = low.lastIndexOf(" by ");
            if (by > 0) p = p.substring(by + 4) + " " + p.substring(0, by); // "Title by Artist"
            out.append(out.length() > 0 ? " " : "").append(p);
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    /** "Thailand · pop, hits · 128 kbps AAC" */
    static String describe(Station s) {
        StringBuilder b = new StringBuilder();
        if (!s.country.isEmpty()) b.append(s.country);
        String tags = s.tags.replace(",", ", ");
        if (tags.length() > 34) tags = tags.substring(0, tags.lastIndexOf(',', 34) > 0 ? tags.lastIndexOf(',', 34) : 34);
        if (!tags.isEmpty()) b.append(b.length() > 0 ? "  ·  " : "").append(tags);
        if (s.bitrate > 0) b.append(b.length() > 0 ? "  ·  " : "").append(s.bitrate).append(" kbps ").append(s.codec);
        return b.toString();
    }
}
