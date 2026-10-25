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

    /** {label, query path}. */
    static final String[][] SECTIONS = {
            {"Popular in Thailand", "stations/search?countrycode=TH&order=clickcount&reverse=true"},
            {"Top worldwide", "stations/search?order=clickcount&reverse=true"},
            {"UK", "stations/search?countrycode=GB&order=clickcount&reverse=true"},
            {"US", "stations/search?countrycode=US&order=clickcount&reverse=true"},
            {"News & talk", "stations/search?tag=news&language=english&order=clickcount&reverse=true"},
            {"Chill & lo-fi", "stations/search?tag=chillout&order=clickcount&reverse=true"},
            {"Jazz", "stations/search?tag=jazz&order=clickcount&reverse=true"},
            {"Classical", "stations/search?tag=classical&order=clickcount&reverse=true"},
            {"Dance & electronic", "stations/search?tag=dance&order=clickcount&reverse=true"},
            {"Rock", "stations/search?tag=rock&order=clickcount&reverse=true"},
            {"Thai music", "stations/search?tag=thai&order=clickcount&reverse=true"},
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
