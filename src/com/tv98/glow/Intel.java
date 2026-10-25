package com.tv98.glow;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The Intelligence screens' data, all from free public sources (no keys): USGS earthquakes, GDACS disaster alerts,
 * NASA EONET natural events, the ISS position, World Monitor's public headlines placed on the map by country,
 * Polymarket's most-traded questions, and Open-Meteo's forecast and air quality. Each source is cached on disk with
 * its own refresh time, so screens open instantly and the box doesn't over-ask anyone.
 */
final class Intel {
    static final String QUAKE = "quake", ALERT = "alert", NATURAL = "natural", NEWS = "news", CAM = "cam";

    static final class Event {
        String kind, title, detail, source, link, category;
        double lat, lon;
        long at;
        /** 0 low, 1 notable, 2 severe. */
        int severity;
        /** News hotspots: how many headlines name the place. Cams: index into WmCams.CAMS. */
        int count;
    }

    static final class Market {
        String question;
        double yes;
        double volume24h;
        String endDate;
    }

    static final class Weather {
        String city;
        double temp, feels, max, min;
        int code = -1, aqi = -1;
        double pm25 = -1;
        long sunrise, sunset;
        final List<long[]> hourly = new ArrayList<>();   // {time, temp*10, code, rain%}
        final List<long[]> daily = new ArrayList<>();    // {time, max*10, min*10, code, rain%}
    }

    private Intel() {}

    // ---- cache ----

    private static String cached(Context c, String name, String url, long freshMs) {
        File f = new File(c.getFilesDir(), "intel-" + name + ".json");
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < freshMs) {
            String s = read(f);
            if (s != null) return s;
        }
        try {
            String body = new String(Net.bytes(url, 4 * 1024 * 1024), "UTF-8");
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(body.getBytes("UTF-8"));
            }
            return body;
        } catch (Exception e) {
            return f.exists() ? read(f) : null; // an older copy beats nothing
        }
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

    // ---- map events ----

    /** USGS: magnitude 4.5+ in the last day. */
    static List<Event> quakes(Context c) {
        List<Event> out = new ArrayList<>();
        String s = cached(c, "usgs", "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_day.geojson", 10 * 60_000L);
        if (s == null) return out;
        try {
            JSONArray f = new JSONObject(s).getJSONArray("features");
            for (int i = 0; i < f.length(); i++) {
                JSONObject p = f.getJSONObject(i).getJSONObject("properties");
                JSONArray xy = f.getJSONObject(i).getJSONObject("geometry").getJSONArray("coordinates");
                Event e = new Event();
                e.kind = QUAKE;
                double mag = p.optDouble("mag", 0);
                e.title = String.format(Locale.ROOT, "M%.1f earthquake", mag);
                e.detail = p.optString("place") + "  ·  depth " + Math.round(xy.optDouble(2)) + " km"
                        + (p.optInt("tsunami") == 1 ? "  ·  tsunami alert" : "");
                e.source = "USGS";
                e.link = p.optString("url");
                e.lon = xy.getDouble(0);
                e.lat = xy.getDouble(1);
                e.at = p.optLong("time");
                e.severity = mag >= 6.5 || p.optInt("tsunami") == 1 ? 2 : mag >= 5.5 ? 1 : 0;
                e.category = "Earthquake";
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** GDACS (UN/EU): current orange and red alerts for earthquakes, cyclones, floods, volcanoes, droughts, wildfires. */
    static List<Event> alerts(Context c) {
        List<Event> out = new ArrayList<>();
        String s = cached(c, "gdacs", "https://www.gdacs.org/gdacsapi/api/events/geteventlist/SEARCH?eventlist=EQ;TC;FL;VO;DR;WF",
                30 * 60_000L);
        if (s == null) return out;
        Map<String, String> kinds = new HashMap<>();
        kinds.put("EQ", "Earthquake");
        kinds.put("TC", "Tropical cyclone");
        kinds.put("FL", "Flood");
        kinds.put("VO", "Volcano");
        kinds.put("DR", "Drought");
        kinds.put("WF", "Wildfire");
        try {
            JSONArray f = new JSONObject(s).getJSONArray("features");
            for (int i = 0; i < f.length(); i++) {
                JSONObject feat = f.getJSONObject(i);
                JSONObject p = feat.getJSONObject("properties");
                String level = p.optString("alertlevel");
                if (!level.equals("Red") && !level.equals("Orange")) continue;
                // The list reaches back months; the map is about now (still going, or ended this week).
                long to = gdacsTime(p.optString("todate"));
                if (!p.optBoolean("iscurrent") && System.currentTimeMillis() - to > 7 * 86_400_000L) continue;
                double[] ll = firstPoint(feat.optJSONObject("geometry"));
                if (ll == null) continue;
                Event e = new Event();
                e.kind = ALERT;
                e.category = kinds.containsKey(p.optString("eventtype")) ? kinds.get(p.optString("eventtype")) : "Disaster";
                e.title = p.optString("name", e.category);
                String where = p.optString("country");
                e.detail = level + " alert" + (where.isEmpty() ? "" : "  ·  " + where);
                e.source = "GDACS";
                JSONObject urls = p.optJSONObject("url");
                e.link = urls == null ? "" : urls.optString("report");
                e.lon = ll[0];
                e.lat = ll[1];
                e.at = gdacsTime(p.optString("fromdate"));
                e.severity = level.equals("Red") ? 2 : 1;
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** NASA EONET: open natural events of the last 30 days (wildfires, storms, volcanoes, ice). */
    static List<Event> natural(Context c) {
        List<Event> out = new ArrayList<>();
        String s = cached(c, "eonet", "https://eonet.gsfc.nasa.gov/api/v3/events?status=open&days=30", 30 * 60_000L);
        if (s == null) return out;
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            JSONArray ev = new JSONObject(s).getJSONArray("events");
            for (int i = 0; i < ev.length(); i++) {
                JSONObject o = ev.getJSONObject(i);
                JSONArray geo = o.optJSONArray("geometry");
                if (geo == null || geo.length() == 0) continue;
                JSONObject last = geo.getJSONObject(geo.length() - 1);
                double[] ll = firstPoint(last);
                if (ll == null) continue;
                Event e = new Event();
                e.kind = NATURAL;
                e.category = o.getJSONArray("categories").getJSONObject(0).optString("title");
                e.title = o.optString("title");
                e.detail = e.category;
                e.source = "NASA EONET";
                e.link = o.optString("link");
                e.lon = ll[0];
                e.lat = ll[1];
                try {
                    e.at = iso.parse(last.optString("date")).getTime();
                } catch (Exception ignored) {
                }
                e.severity = e.category.startsWith("Severe") || e.category.startsWith("Volcano") ? 1 : 0;
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Headlines placed on their country: one hotspot per country with how many top stories name it. */
    static List<Event> newsHotspots(Context c) {
        Map<String, Event> by = new HashMap<>();
        for (World.Story s : World.section(World.cached(c), "__top")) {
            for (Countries.Country k : Countries.mentioned(c, s.title)) {
                Event e = by.get(k.iso);
                if (e == null) {
                    e = new Event();
                    e.kind = NEWS;
                    e.category = "News";
                    e.title = k.name;
                    e.detail = s.title;
                    e.source = s.source;
                    e.link = s.link;
                    e.lat = k.lat;
                    e.lon = k.lon;
                    e.at = s.at;
                    by.put(k.iso, e);
                }
                e.count++;
                boolean alert = s.alert || "CRITICAL".equals(s.threat) || "HIGH".equals(s.threat);
                if (alert) e.severity = Math.max(e.severity, 1);
            }
        }
        List<Event> out = new ArrayList<>(by.values());
        for (Event e : out) {
            if (e.count >= 4) e.severity = 2;
            e.title = e.title + "  ·  " + e.count + (e.count == 1 ? " story" : " stories");
        }
        Collections.sort(out, (a, b) -> b.count - a.count);
        return out;
    }

    /** Live webcams with a fixed place, as map pins. */
    static List<Event> cams() {
        List<Event> out = new ArrayList<>();
        for (int i = 0; i < WmCams.CAMS.length; i++) {
            String[] r = WmCams.CAMS[i];
            if (r[4].isEmpty()) continue;
            Event e = new Event();
            e.kind = CAM;
            e.category = "Live cam";
            e.title = r[1] + " live cam";
            e.detail = r[2] + "  ·  " + r[3];
            e.source = "YouTube";
            e.lat = Double.parseDouble(r[4]);
            e.lon = Double.parseDouble(r[5]);
            e.count = i;
            out.add(e);
        }
        return out;
    }

    /** Where the International Space Station is now, or null. Not cached (it moves 7.7 km a second). */
    static double[] iss() {
        try {
            JSONObject o = new JSONObject(Net.text("https://api.wheretheiss.at/v1/satellites/25544"));
            return new double[] {o.getDouble("latitude"), o.getDouble("longitude")};
        } catch (Exception e) {
            return null;
        }
    }

    private static double[] firstPoint(JSONObject geometry) {
        if (geometry == null) return null;
        try {
            Object c = geometry.get("coordinates");
            while (c instanceof JSONArray && ((JSONArray) c).length() > 0 && ((JSONArray) c).get(0) instanceof JSONArray) {
                c = ((JSONArray) c).get(0);
            }
            JSONArray xy = (JSONArray) c;
            return new double[] {xy.getDouble(0), xy.getDouble(1)};
        } catch (Exception e) {
            return null;
        }
    }

    private static long gdacsTime(String s) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(s).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    // ---- prediction markets ----

    /** Polymarket's most-traded open questions this day, minus sports and price bets (this is about the world). */
    static List<Market> markets(Context c) {
        List<Market> out = new ArrayList<>();
        String s = cached(c, "polymarket",
                "https://gamma-api.polymarket.com/markets?active=true&closed=false&order=volume24hr&ascending=false&limit=60", 15 * 60_000L);
        if (s == null) return out;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length() && out.size() < 24; i++) {
                JSONObject m = a.getJSONObject(i);
                String q = m.optString("question");
                String lq = q.toLowerCase(Locale.ROOT);
                if (lq.contains(" vs. ") || lq.contains(" vs ") || lq.contains("o/u") || lq.contains("spread")
                        || lq.contains("up or down") || lq.contains("price of") || lq.matches(".*\\b(fc|nba|nfl|mlb|nhl|ufc|epl)\\b.*")) continue;
                JSONArray prices = new JSONArray(m.optString("outcomePrices", "[]"));
                if (prices.length() < 1) continue;
                Market k = new Market();
                k.question = q;
                k.yes = prices.optDouble(0, 0);
                k.volume24h = m.optDouble("volume24hr", 0);
                k.endDate = m.optString("endDate");
                out.add(k);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---- weather ----

    static Weather weather(Context c) {
        double[] ll = com.tv98.glow.Weather.location(c);
        String q = String.format(Locale.US, "latitude=%.2f&longitude=%.2f", ll[0], ll[1]);
        String f = cached(c, "forecast", "https://api.open-meteo.com/v1/forecast?" + q
                + "&current=temperature_2m,apparent_temperature,weather_code"
                + "&hourly=temperature_2m,weather_code,precipitation_probability"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
                + "&timezone=auto&forecast_days=7&timeformat=unixtime", 30 * 60_000L);
        String a = cached(c, "air", "https://air-quality-api.open-meteo.com/v1/air-quality?" + q + "&current=us_aqi,pm2_5", 60 * 60_000L);
        Weather w = new Weather();
        w.city = com.tv98.glow.Weather.cityName(c);
        if (f == null) return w;
        try {
            JSONObject o = new JSONObject(f);
            JSONObject cur = o.getJSONObject("current");
            w.temp = cur.optDouble("temperature_2m");
            w.feels = cur.optDouble("apparent_temperature");
            w.code = cur.optInt("weather_code");
            JSONObject h = o.getJSONObject("hourly");
            JSONArray ht = h.getJSONArray("time"), htemp = h.getJSONArray("temperature_2m"), hc = h.getJSONArray("weather_code"),
                    hp = h.getJSONArray("precipitation_probability");
            long now = System.currentTimeMillis() / 1000 - 3600;
            for (int i = 0; i < ht.length() && w.hourly.size() < 24; i++) {
                if (ht.getLong(i) < now) continue;
                w.hourly.add(new long[] {ht.getLong(i) * 1000, Math.round(htemp.optDouble(i) * 10), hc.optInt(i), hp.optInt(i)});
            }
            JSONObject d = o.getJSONObject("daily");
            JSONArray dt = d.getJSONArray("time");
            for (int i = 0; i < dt.length(); i++) {
                w.daily.add(new long[] {dt.getLong(i) * 1000, Math.round(d.getJSONArray("temperature_2m_max").optDouble(i) * 10),
                        Math.round(d.getJSONArray("temperature_2m_min").optDouble(i) * 10), d.getJSONArray("weather_code").optInt(i),
                        d.getJSONArray("precipitation_probability_max").optInt(i)});
            }
            if (!w.daily.isEmpty()) {
                w.max = w.daily.get(0)[1] / 10.0;
                w.min = w.daily.get(0)[2] / 10.0;
            }
            w.sunrise = d.getJSONArray("sunrise").optLong(0) * 1000;
            w.sunset = d.getJSONArray("sunset").optLong(0) * 1000;
        } catch (Exception ignored) {
        }
        if (a != null) {
            try {
                JSONObject cur = new JSONObject(a).getJSONObject("current");
                w.aqi = cur.optInt("us_aqi", -1);
                w.pm25 = cur.optDouble("pm2_5", -1);
            } catch (Exception ignored) {
            }
        }
        return w;
    }

    /** US AQI band: {label, colour}. */
    static Object[] aqiBand(int aqi) {
        if (aqi < 0) return new Object[] {"", 0xFF8FA8FF};
        if (aqi <= 50) return new Object[] {"Good", 0xFF6BE3A0};
        if (aqi <= 100) return new Object[] {"Moderate", 0xFFF5D33C};
        if (aqi <= 150) return new Object[] {"Unhealthy for sensitive groups", 0xFFFFA14A};
        if (aqi <= 200) return new Object[] {"Unhealthy", 0xFFFF6B6B};
        if (aqi <= 300) return new Object[] {"Very unhealthy", 0xFFC77DFF};
        return new Object[] {"Hazardous", 0xFFB0304A};
    }
}
