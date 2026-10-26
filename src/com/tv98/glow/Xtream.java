package com.tv98.glow;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for the standard Xtream Codes "player_api.php" interface that IPTV providers expose.
 * Credentials are whatever the user's provider issued; they stay in this app's private storage.
 */
final class Xtream {
    static final class Account {
        final String base, user, pass;

        Account(String base, String user, String pass) {
            this.base = base;
            this.user = user;
            this.pass = pass;
        }
    }

    static final class Category {
        final String id, name;

        Category(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }


    /** Result of a sign-in check, phrased for people rather than for the API. */
    static final class Status {
        final boolean ok;
        final String message;

        Status(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    /** The channel list the player zaps through; set by whoever launches the player. */
    static List<Channel> playlist = new ArrayList<>();

    private static final String PREFS = "xtream";

    private Xtream() {}

    static String normalizeBase(String raw) {
        String s = raw.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.endsWith("/player_api.php")) s = s.substring(0, s.length() - "/player_api.php".length());
        return s;
    }

    static Account load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String b = p.getString("base", null), u = p.getString("user", null), w = p.getString("pass", null);
        return b == null || u == null || w == null ? null : new Account(b, u, w);
    }

    static void save(Context c, Account a) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("base", a.base).putString("user", a.user).putString("pass", a.pass).apply();
    }

    static void signOut(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        new File(c.getFilesDir(), "streams.json").delete();
        playlist = new ArrayList<>();
    }

    private static String api(Account a, String action, String extra) {
        Uri.Builder b = Uri.parse(a.base + "/player_api.php").buildUpon()
                .appendQueryParameter("username", a.user)
                .appendQueryParameter("password", a.pass);
        if (action != null) b.appendQueryParameter("action", action);
        if (extra != null) b.appendQueryParameter("category_id", extra);
        return b.build().toString();
    }

    static Status check(Account a) {
        try {
            String body = Net.text(api(a, null, null));
            JSONObject info = new JSONObject(body).optJSONObject("user_info");
            if (info == null || info.optInt("auth", 0) != 1) {
                return new Status(false, "The provider didn't accept this username or password.");
            }
            String status = info.optString("status", "");
            long exp = info.optLong("exp_date", 0);
            String day = exp > 0 ? L.date("d MMM yyyy", exp * 1000L).toString() : null;
            if ("Expired".equalsIgnoreCase(status)) {
                return new Status(false, "This subscription expired" + (day != null ? " on " + day : "") + ".");
            }
            if (!"Active".equalsIgnoreCase(status)) {
                return new Status(false, "The provider says this account is " + status.toLowerCase() + ".");
            }
            return new Status(true, "Active" + (day != null ? " until " + day : "") + ".");
        } catch (JSONException e) {
            return new Status(false, "That server didn't answer like an IPTV server. Check the address.");
        } catch (IOException e) {
            return new Status(false, "Couldn't reach the server (" + e.getMessage() + ").");
        }
    }

    static List<Category> categories(Account a) throws IOException, JSONException {
        JSONArray arr = new JSONArray(Net.text(api(a, "get_live_categories", null)));
        List<Category> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(new Category(o.optString("category_id"), o.optString("category_name", "Channels")));
        }
        return out;
    }

    /** All live channels; also cached on disk so the home row appears instantly next time. */
    static List<Channel> channels(Context c, Account a) throws IOException, JSONException {
        String body = Net.text(api(a, "get_live_streams", null));
        List<Channel> list = parse(body);
        try (FileOutputStream f = new FileOutputStream(new File(c.getFilesDir(), "streams.json"))) {
            f.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return list;
    }

    static List<Channel> cached(Context c) {
        File f = new File(c.getFilesDir(), "streams.json");
        if (!f.exists()) return new ArrayList<>();
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return parse(new String(b, StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            return new ArrayList<>();
        }
    }

    private static List<Channel> parse(String body) throws JSONException {
        JSONArray arr = new JSONArray(body);
        List<Channel> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(new Channel(o.optInt("stream_id"), o.optInt("num", i + 1), o.optString("name").trim(),
                    o.optString("stream_icon", ""), o.optString("category_id", "")));
        }
        return out;
    }

    static List<Channel> inCategory(List<Channel> all, String categoryId) {
        if (categoryId == null) return all;
        List<Channel> out = new ArrayList<>();
        for (Channel ch : all) if (categoryId.equals(ch.categoryId)) out.add(ch);
        return out;
    }

    static String streamUrl(Account a, Channel ch, String ext) {
        if (ch.url != null) return ch.url;
        return a.base + "/live/" + Uri.encode(a.user) + "/" + Uri.encode(a.pass) + "/" + ch.id + "." + ext;
    }


}
