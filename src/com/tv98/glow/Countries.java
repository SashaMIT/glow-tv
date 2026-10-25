package com.tv98.glow;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Countries from Natural Earth (public domain): a label point for the map, and the names headlines use for them. */
final class Countries {
    static final class Country {
        final String iso, name;
        final double lat, lon;
        final String[] phrases;

        Country(String iso, String name, double lat, double lon, String[] phrases) {
            this.iso = iso;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.phrases = phrases;
        }
    }

    private static List<Country> all;

    private Countries() {}

    static synchronized List<Country> all(Context c) {
        if (all != null) return all;
        List<Country> out = new ArrayList<>();
        int id = c.getResources().getIdentifier("countries", "raw", c.getPackageName());
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getResources().openRawResource(id), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                String[] f = line.split("\t");
                if (f.length < 5) continue;
                out.add(new Country(f[0], f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3]), f[4].split("\\|")));
            }
        } catch (Exception ignored) {
        }
        all = out;
        return out;
    }

    /** Countries a headline names (whole words; "uk" doesn't match "ukraine"), most specific phrase first. */
    static List<Country> mentioned(Context c, String text) {
        String t = " " + text.toLowerCase(Locale.ROOT) + " ";
        List<Country> out = new ArrayList<>();
        for (Country k : all(c)) {
            for (String p : k.phrases) {
                // Two-letter forms ("UK") only count in capitals, so "us" the word doesn't put a pin in America.
                boolean hit = p.length() >= 3 ? World.hasWord(t, p)
                        : p.length() == 2 && World.hasWord(" " + text + " ", p.toUpperCase(Locale.ROOT));
                if (hit) {
                    out.add(k);
                    break;
                }
            }
        }
        return out;
    }
}
