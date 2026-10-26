package com.tv98.glow;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The full timing sheet of the latest finished F1 session from OpenF1's free data (positions, gaps, laps and the
 * tyres each driver used). OpenF1's live timing during a session is their paid tier, so Glow shows a session once
 * it has ended; during a race Sports now has ESPN's running order.
 */
final class F1Timing {
    static final class Row {
        int pos;
        String code = "", name = "", team = "", gap = "";
        int colour = 0xFF888888, laps;
        final List<String> tyres = new ArrayList<>();
    }

    static final class Sheet {
        String session = "", place = "";
        long ended;
        final List<Row> rows = new ArrayList<>();
    }

    private static final String API = "https://api.openf1.org/v1/";

    private F1Timing() {}

    /** Off the main thread. The newest finished session that has results, or null (offline, or none yet). */
    static Sheet latest(Context c) {
        int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        String all = Intel.cached(c, "f1-sessions-" + year, API + "sessions?year=" + year, 3_600_000L);
        if (all == null) return null;
        try {
            JSONArray s = new JSONArray(all);
            long now = System.currentTimeMillis();
            // Newest first; a session only just ended may not have its results yet, so try a couple.
            List<JSONObject> ended = new ArrayList<>();
            for (int i = 0; i < s.length(); i++) {
                JSONObject x = s.getJSONObject(i);
                if (!x.optBoolean("is_cancelled") && iso(x.optString("date_end")) < now) ended.add(x);
            }
            java.util.Collections.sort(ended, (a, b) -> Long.compare(iso(b.optString("date_end")), iso(a.optString("date_end"))));
            for (int i = 0; i < Math.min(3, ended.size()); i++) {
                Sheet sh = sheet(c, ended.get(i), now);
                if (sh != null && !sh.rows.isEmpty()) return sh;
            }
        } catch (Exception e) {
            android.util.Log.i("GlowPerf", "f1 timing: " + e);
        }
        return null;
    }

    private static Sheet sheet(Context c, JSONObject session, long now) throws Exception {
        String key = session.optString("session_key");
        long ended = iso(session.optString("date_end"));
        // Results settle within a few hours; after that they're kept for a week.
        long fresh = now - ended < 3 * 3_600_000L ? 10 * 60_000L : 7 * 86_400_000L;
        String res = Intel.cached(c, "f1-result-" + key, API + "session_result?session_key=" + key, fresh);
        if (res == null || !res.trim().startsWith("[")) return null;
        String drv = Intel.cached(c, "f1-drivers-" + key, API + "drivers?session_key=" + key, 7 * 86_400_000L);
        String sti = Intel.cached(c, "f1-stints-" + key, API + "stints?session_key=" + key, fresh);
        Map<Integer, JSONObject> drivers = new HashMap<>();
        if (drv != null && drv.trim().startsWith("[")) {
            JSONArray d = new JSONArray(drv);
            for (int i = 0; i < d.length(); i++) drivers.put(d.getJSONObject(i).optInt("driver_number"), d.getJSONObject(i));
        }
        Map<Integer, List<String>> tyres = new HashMap<>();
        if (sti != null && sti.trim().startsWith("[")) {
            JSONArray t = new JSONArray(sti);
            List<JSONObject> stints = new ArrayList<>();
            for (int i = 0; i < t.length(); i++) stints.add(t.getJSONObject(i));
            java.util.Collections.sort(stints, (a, b) -> a.optInt("stint_number") - b.optInt("stint_number"));
            for (JSONObject st : stints) {
                List<String> l = tyres.get(st.optInt("driver_number"));
                if (l == null) tyres.put(st.optInt("driver_number"), l = new ArrayList<>());
                l.add(st.optString("compound"));
            }
        }
        boolean race = session.optString("session_type").equals("Race");
        Sheet sh = new Sheet();
        sh.session = session.optString("session_name");
        sh.place = session.optString("location", session.optString("circuit_short_name"));
        sh.ended = ended;
        JSONArray r = new JSONArray(res);
        for (int i = 0; i < r.length(); i++) {
            JSONObject x = r.getJSONObject(i);
            Row row = new Row();
            int n = x.optInt("driver_number");
            row.pos = x.isNull("position") ? 0 : x.optInt("position");
            row.laps = x.optInt("number_of_laps");
            JSONObject d = drivers.get(n);
            if (d != null) {
                row.code = d.optString("name_acronym");
                row.name = d.optString("first_name") + " " + d.optString("last_name");
                row.team = d.optString("team_name");
                try {
                    row.colour = 0xFF000000 | Integer.parseInt(d.optString("team_colour", "888888"), 16);
                } catch (NumberFormatException ignored) {
                }
            } else {
                row.code = "#" + n;
            }
            if (tyres.containsKey(n)) row.tyres.addAll(tyres.get(n));
            row.gap = gap(x, race, row.pos == 1);
            sh.rows.add(row);
        }
        // Finishers in order, then retirements.
        java.util.Collections.sort(sh.rows, (a, b) -> (a.pos == 0 ? 999 : a.pos) - (b.pos == 0 ? 999 : b.pos));
        return sh;
    }

    /** "+13.078", "+1 LAP", "1:34.035" (the leader's lap or race time), "DNF". */
    private static String gap(JSONObject x, boolean race, boolean leader) {
        if (x.optBoolean("dsq")) return "DSQ";
        if (x.optBoolean("dns")) return "DNS";
        if (x.optBoolean("dnf")) return "DNF";
        Object g = last(x.opt("gap_to_leader")), d = last(x.opt("duration"));
        if (leader) return d instanceof Number ? clock(((Number) d).doubleValue(), race) : "";
        if (g instanceof Number) return String.format(Locale.ROOT, "+%.3f", ((Number) g).doubleValue());
        return g == null || g == JSONObject.NULL ? "" : String.valueOf(g);
    }

    /** Qualifying gives [Q1, Q2, Q3]: the last one the driver reached. */
    private static Object last(Object v) {
        if (!(v instanceof JSONArray)) return v;
        JSONArray a = (JSONArray) v;
        for (int i = a.length() - 1; i >= 0; i--) if (!a.isNull(i)) return a.opt(i);
        return null;
    }

    private static String clock(double sec, boolean hours) {
        int h = (int) (sec / 3600), m = (int) (sec % 3600 / 60);
        double s = sec % 60;
        return hours && h > 0 ? String.format(Locale.ROOT, "%d:%02d:%06.3f", h, m, s) : String.format(Locale.ROOT, "%d:%06.3f", h * 60 + m, s);
    }

    /** OpenF1 times: 2026-10-25T22:00:00+00:00. */
    static long iso(String s) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT);
            return f.parse(s.length() > 25 ? s.substring(0, 19) + s.substring(s.length() - 6) : s).getTime();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    /** Tyre colours as on the broadcast. */
    static int tyreColour(String compound) {
        switch (compound) {
            case "SOFT": return 0xFFE8002D;
            case "MEDIUM": return 0xFFFFD12E;
            case "HARD": return 0xFFF0F0F0;
            case "INTERMEDIATE": return 0xFF43B02A;
            case "WET": return 0xFF0067AD;
            default: return 0xFF777777;
        }
    }
}
