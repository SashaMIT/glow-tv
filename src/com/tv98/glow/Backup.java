package com.tv98.glow;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Glow's settings as one JSON file for the phone (phone remote ▸ More): apps on Home, favourites and channel state,
 * display, sports, player languages, screensaver, reminders. Never the IPTV sign-in ("xtream" isn't included).
 */
final class Backup {
    private static final String[] FILES = {"apps", "channels", "display", "dream", "player", "sports", "reminders"};
    private static final Set<String> SKIP = new HashSet<>(java.util.Arrays.asList("bandwidth")); // box-specific

    private Backup() {}

    static JSONObject export(Context c) throws Exception {
        JSONObject root = new JSONObject().put("glow_backup", 1).put("made", System.currentTimeMillis());
        JSONObject files = new JSONObject();
        for (String name : FILES) {
            JSONObject f = new JSONObject();
            for (Map.Entry<String, ?> e : c.getSharedPreferences(name, Context.MODE_PRIVATE).getAll().entrySet()) {
                if (SKIP.contains(e.getKey())) continue;
                Object v = e.getValue();
                JSONObject item = new JSONObject();
                if (v instanceof Set) item.put("t", "set").put("v", new JSONArray((Set<?>) v));
                else if (v instanceof Boolean) item.put("t", "bool").put("v", v);
                else if (v instanceof Integer) item.put("t", "int").put("v", v);
                else if (v instanceof Long) item.put("t", "long").put("v", v);
                else if (v instanceof Float) item.put("t", "float").put("v", ((Float) v).doubleValue());
                else item.put("t", "str").put("v", String.valueOf(v));
                f.put(e.getKey(), item);
            }
            files.put(name, f);
        }
        return root.put("prefs", files);
    }

    /** Replaces the settings in the backup's files with its values. Returns how many settings were restored. */
    static int restore(Context c, JSONObject root) throws Exception {
        if (root.optInt("glow_backup") != 1) throw new IllegalArgumentException("not a Glow backup");
        JSONObject files = root.getJSONObject("prefs");
        int n = 0;
        for (String name : FILES) {
            JSONObject f = files.optJSONObject(name);
            if (f == null) continue;
            SharedPreferences.Editor ed = c.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear();
            for (Iterator<String> it = f.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject item = f.getJSONObject(k);
                switch (item.optString("t")) {
                    case "set": {
                        Set<String> s = new HashSet<>();
                        JSONArray a = item.getJSONArray("v");
                        for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                        ed.putStringSet(k, s);
                        break;
                    }
                    case "bool": ed.putBoolean(k, item.getBoolean("v")); break;
                    case "int": ed.putInt(k, item.getInt("v")); break;
                    case "long": ed.putLong(k, item.getLong("v")); break;
                    case "float": ed.putFloat(k, (float) item.getDouble("v")); break;
                    default: ed.putString(k, item.getString("v"));
                }
                n++;
            }
            ed.commit();
        }
        GuideLineup.invalidate();
        Sports.invalidate();
        return n;
    }
}
