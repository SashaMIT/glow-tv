package com.tv98.glow;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Your own Home Assistant on the TV: a Home card with the sensors you pick, and a pop-up with the camera picture when
 * the doorbell rings, over whatever's on. Set up from the phone remote (More ▸ Home Assistant) with the server's
 * address and a long-lived access token; both stay on this box (not in the settings backup) and Glow only reads.
 */
final class HomeAssistant {
    static final class Entity {
        String id, name, state, unit;
    }

    private HomeAssistant() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ha", Context.MODE_PRIVATE);
    }

    static boolean configured(Context c) {
        return !prefs(c).getString("url", "").isEmpty() && !prefs(c).getString("token", "").isEmpty();
    }

    /** What the phone sees: never the token itself. */
    static JSONObject config(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        return new JSONObject().put("url", p.getString("url", "")).put("connected", configured(c))
                .put("entities", new JSONArray(p.getString("entities", "[]"))).put("doorbell", p.getString("doorbell", ""))
                .put("camera", p.getString("camera", ""));
    }

    /**
     * From the phone: {url, token} connects (checked against the server first), {entities, doorbell, camera} picks
     * what's shown, {forget: true} removes everything. Returns {ok, error}. Off the main thread.
     */
    static JSONObject set(Context c, JSONObject in) throws Exception {
        SharedPreferences.Editor e = prefs(c).edit();
        if (in.optBoolean("forget")) {
            prefs(c).edit().clear().apply();
            return new JSONObject().put("ok", true);
        }
        if (in.has("url")) {
            String url = in.optString("url").trim().replaceAll("/+$", "");
            if (!url.matches("https?://.+")) url = "http://" + url;
            String token = in.optString("token").trim();
            try {
                JSONObject r = new JSONObject(new String(get(url + "/api/", token, 64 * 1024), "UTF-8"));
                if (!r.has("message")) throw new IOException("not Home Assistant");
            } catch (Exception ex) {
                return new JSONObject().put("ok", false).put("error", ex.getMessage() != null && ex.getMessage().contains("401")
                        ? "The token wasn't accepted. Make a long-lived access token in your Home Assistant profile."
                        : "Couldn't reach Home Assistant at " + url + ". Use its address on your network, e.g. http://192.168.1.20:8123");
            }
            e.putString("url", url).putString("token", token);
        }
        if (in.has("entities")) e.putString("entities", in.getJSONArray("entities").toString());
        if (in.has("doorbell")) e.putString("doorbell", in.optString("doorbell"));
        if (in.has("camera")) e.putString("camera", in.optString("camera"));
        e.apply();
        last = null;
        return new JSONObject().put("ok", true);
    }

    /** {entities: […]} for the phone's picker, or {error: "…"}. */
    static JSONObject entities(Context c) throws Exception {
        if (!configured(c)) return new JSONObject().put("error", "Connect to Home Assistant first.");
        try {
            return new JSONObject().put("entities", all(c));
        } catch (Exception e) {
            return new JSONObject().put("error", "Couldn't read from Home Assistant (" + e.getMessage() + ").");
        }
    }

    /** Every sensor, switch, lock, light, camera and doorbell, with its name. */
    private static JSONArray all(Context c) throws Exception {
        JSONArray states = new JSONArray(new String(call(c, "/api/states", 4 << 20), "UTF-8"));
        JSONArray out = new JSONArray();
        for (int i = 0; i < states.length(); i++) {
            JSONObject s = states.getJSONObject(i);
            String id = s.optString("entity_id");
            String domain = id.substring(0, Math.max(0, id.indexOf('.')));
            if (!domain.matches("sensor|binary_sensor|switch|light|lock|climate|cover|camera|event|person|weather|alarm_control_panel")) continue;
            JSONObject a = s.optJSONObject("attributes");
            out.put(new JSONObject().put("id", id).put("name", a == null ? id : a.optString("friendly_name", id))
                    .put("state", s.optString("state")).put("unit", a == null ? "" : a.optString("unit_of_measurement")));
        }
        return out;
    }

    private static List<Entity> last;
    private static long lastAt;

    /** The picked entities' states, for Home's card (reused for 30 s). Off the main thread. */
    static synchronized List<Entity> states(Context c) {
        if (last != null && System.currentTimeMillis() - lastAt < 30_000) return last;
        List<Entity> out = new ArrayList<>();
        try {
            JSONArray ids = new JSONArray(prefs(c).getString("entities", "[]"));
            for (int i = 0; i < ids.length() && i < 6; i++) {
                JSONObject s = new JSONObject(new String(call(c, "/api/states/" + ids.getString(i), 256 * 1024), "UTF-8"));
                JSONObject a = s.optJSONObject("attributes");
                Entity e = new Entity();
                e.id = s.optString("entity_id");
                e.name = a == null ? e.id : a.optString("friendly_name", e.id);
                e.state = s.optString("state");
                e.unit = a == null ? "" : a.optString("unit_of_measurement");
                out.add(e);
            }
        } catch (Exception e) {
            android.util.Log.i("GlowPerf", "home assistant: " + e);
        }
        last = out;
        lastAt = System.currentTimeMillis();
        return out;
    }

    /** "Living room 23.5 °C", "Front door Locked". */
    static String describe(Entity e) {
        String state = e.state;
        if (!state.equals("on") && !state.equals("off") && !state.isEmpty() && Character.isLetter(state.charAt(0))) state = Character.toUpperCase(state.charAt(0)) + state.substring(1).replace('_', ' ');
        return e.name + "  " + state + (e.unit == null || e.unit.isEmpty() ? "" : " " + e.unit);
    }

    // ---- the doorbell ----

    private static String bell;

    /**
     * Checked every few seconds while the box is on. A doorbell binary_sensor turning on, or an event entity's
     * timestamp changing, shows the camera's picture over whatever's on. Off the main thread.
     */
    static void checkDoorbell(Context c) {
        String id = prefs(c).getString("doorbell", "");
        if (id.isEmpty() || !configured(c)) return;
        String state;
        try {
            state = new JSONObject(new String(call(c, "/api/states/" + id, 256 * 1024), "UTF-8")).optString("state");
        } catch (Exception e) {
            return;
        }
        String was = bell;
        bell = state;
        boolean rang = was != null && !state.equals(was)
                && (id.startsWith("event.") ? !state.equals("unavailable") : state.equals("on"));
        if (rang) ring(c, false);
    }

    /** Shows the doorbell pop-up (also the phone's "Test" button). Off the main thread. */
    static void ring(Context c, boolean test) {
        String cam = prefs(c).getString("camera", "");
        Bitmap pic = null;
        if (!cam.isEmpty()) {
            try {
                byte[] jpg = call(c, "/api/camera_proxy/" + cam, 4 << 20);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2; // a 1080p snapshot is far more than a 480 px pop-up needs
                pic = BitmapFactory.decodeByteArray(jpg, 0, jpg.length, o);
            } catch (Exception e) {
                android.util.Log.i("GlowPerf", "doorbell camera: " + e);
            }
        }
        final Bitmap shown = pic;
        String when = android.text.format.DateFormat.format(Ui.is24h(c) ? "HH:mm" : "h:mm a", System.currentTimeMillis()).toString();
        Net.MAIN.post(() -> Overlay.picture(c, test ? "Doorbell (test)" : "Someone's at the door", when, shown));
    }

    // ---- HTTP ----

    private static byte[] call(Context c, String path, int limit) throws IOException {
        SharedPreferences p = prefs(c);
        return get(p.getString("url", "") + path, p.getString("token", ""), limit);
    }

    private static byte[] get(String url, String token, int limit) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(4000);
        h.setReadTimeout(8000);
        h.setRequestProperty("Authorization", "Bearer " + token);
        h.setRequestProperty("Content-Type", "application/json");
        try {
            int code = h.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code);
            try (InputStream in = h.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0) {
                    out.write(b, 0, n);
                    if (out.size() > limit) throw new IOException("response too large");
                }
                return out.toByteArray();
            }
        } finally {
            h.disconnect();
        }
    }
}
