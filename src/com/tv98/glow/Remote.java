package com.tv98.glow;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The phone remote: a tiny web server on the home network (port 8099) serving one page.
 * A phone pairs with the 4-digit code shown on the TV, then can type into Glow, search and play channels,
 * zap, press arrow keys and manage favourites. Only private-network addresses are served, wrong codes
 * lock pairing after 5 tries, and the server stops itself after 30 minutes without use.
 */
final class Remote {
    static final int PORT = 8099;
    private static final long IDLE_STOP_MS = 30 * 60 * 1000L;

    private static ServerSocket server;
    private static ExecutorService workers;
    private static Context app;
    private static String code;
    private static int wrongCodes;
    private static final Set<String> tokens = Collections.synchronizedSet(new HashSet<>());
    private static volatile long lastUse;
    private static volatile int phones;
    private static final SecureRandom random = new SecureRandom();

    private Remote() {}

    static synchronized boolean running() {
        return server != null && !server.isClosed();
    }

    static synchronized String code() {
        return code;
    }

    static int phones() {
        return phones;
    }

    /** Starts the server (if needed) and returns the address phones should open, or null if offline. */
    static synchronized String start(Context c) {
        app = c.getApplicationContext();
        lastUse = System.currentTimeMillis();
        String ip = lanAddress();
        if (ip == null) return null;
        if (!running()) {
            try {
                server = new ServerSocket(PORT);
            } catch (Exception e) {
                return null;
            }
            code = String.format(Locale.ROOT, "%04d", random.nextInt(10000));
            wrongCodes = 0;
            workers = Executors.newFixedThreadPool(3);
            Thread accept = new Thread(Remote::acceptLoop, "glow-remote");
            accept.setDaemon(true);
            accept.start();
            Thread idle = new Thread(Remote::idleWatch, "glow-remote-idle");
            idle.setDaemon(true);
            idle.start();
        }
        return "http://" + ip + ":" + PORT;
    }

    static synchronized void stop() {
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        server = null;
        if (workers != null) workers.shutdownNow();
        tokens.clear();
        phones = 0;
    }

    private static void idleWatch() {
        while (running()) {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                return;
            }
            if (System.currentTimeMillis() - lastUse > IDLE_STOP_MS) stop();
        }
    }

    /** First private IPv4 address of this box (Wi-Fi or Ethernet). */
    static String lanAddress() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void acceptLoop() {
        ServerSocket s = server;
        while (s != null && !s.isClosed()) {
            try {
                final Socket client = s.accept();
                InetAddress from = client.getInetAddress();
                if (!(from.isSiteLocalAddress() || from.isLoopbackAddress() || from.isLinkLocalAddress())) {
                    client.close(); // home network only
                    continue;
                }
                workers.execute(() -> handle(client));
            } catch (Exception e) {
                return;
            }
        }
    }

    // ---- HTTP ----

    private static void handle(Socket client) {
        try (Socket c = client) {
            c.setSoTimeout(10_000);
            InputStream in = c.getInputStream();
            // Headers: raw bytes up to the blank line (bodies can be UTF-8, e.g. Thai, so count bytes not chars).
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int prev3 = 0, x;
            while ((x = in.read()) >= 0 && head.size() < 16384) {
                head.write(x);
                prev3 = ((prev3 << 8) | x) & 0xFFFFFFFF;
                if (prev3 == 0x0D0A0D0A) break;
            }
            String[] lines = new String(head.toByteArray(), StandardCharsets.UTF_8).split("\r\n");
            String[] parts = lines[0].split(" ");
            if (parts.length < 2) return;
            String method = parts[0], target = parts[1];
            Map<String, String> headers = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon > 0) headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
            }
            int length = headers.containsKey("content-length") ? Math.min(Integer.parseInt(headers.get("content-length")), 16384) : 0;
            byte[] raw = new byte[length];
            int off = 0, n;
            while (off < length && (n = in.read(raw, off, length - off)) > 0) off += n;
            String body = new String(raw, 0, off, StandardCharsets.UTF_8);
            lastUse = System.currentTimeMillis();
            String path = target.contains("?") ? target.substring(0, target.indexOf('?')) : target;
            String query = target.contains("?") ? target.substring(target.indexOf('?') + 1) : "";
            OutputStream out = c.getOutputStream();

            if (method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) {
                send(out, 200, "text/html; charset=utf-8", page());
                return;
            }
            if (method.equals("POST") && path.equals("/api/pair")) {
                send(out, 200, "application/json", pair(new JSONObject(body.isEmpty() ? "{}" : body)).toString().getBytes(StandardCharsets.UTF_8));
                return;
            }
            String token = headers.get("x-glow-token");
            if (token == null || !tokens.contains(token)) {
                send(out, 401, "application/json", "{\"error\":\"pair first\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            JSONObject in2 = new JSONObject(body.isEmpty() ? "{}" : body);
            Object result;
            switch (path) {
                case "/api/state": result = state(); break;
                case "/api/search": result = search(param(query, "q")); break;
                case "/api/favs": result = favourites(); break;
                case "/api/play": result = play(in2); break;
                case "/api/key": result = key(in2.optString("key")); break;
                case "/api/type": result = type(in2.optString("text"), in2.optBoolean("submit")); break;
                case "/api/fav": result = fav(in2.optString("key")); break;
                default:
                    send(out, 404, "application/json", "{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8));
                    return;
            }
            send(out, 200, "application/json", result.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static void send(OutputStream out, int status, String type, byte[] body) throws Exception {
        String head = "HTTP/1.1 " + status + (status == 200 ? " OK" : " Error") + "\r\nContent-Type: " + type
                + "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private static String param(String query, String name) {
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0 && kv.substring(0, eq).equals(name)) {
                try {
                    return java.net.URLDecoder.decode(kv.substring(eq + 1), "UTF-8");
                } catch (Exception e) {
                    return "";
                }
            }
        }
        return "";
    }

    private static byte[] cachedPage;

    private static byte[] page() throws Exception {
        if (cachedPage == null) {
            try (InputStream in = app.getResources().openRawResource(R.raw.remote)) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                cachedPage = b.toByteArray();
            }
        }
        return cachedPage;
    }

    // ---- API ----

    private static synchronized JSONObject pair(JSONObject in) throws Exception {
        JSONObject out = new JSONObject();
        if (wrongCodes >= 5) {
            code = String.format(Locale.ROOT, "%04d", random.nextInt(10000)); // new code on the TV after 5 misses
            wrongCodes = 0;
            return out.put("ok", false).put("error", "Too many tries — use the new code on the TV.");
        }
        if (!in.optString("code").equals(code)) {
            wrongCodes++;
            return out.put("ok", false).put("error", "That code doesn't match the TV.");
        }
        byte[] t = new byte[16];
        random.nextBytes(t);
        StringBuilder hex = new StringBuilder();
        for (byte x : t) hex.append(String.format(Locale.ROOT, "%02x", x));
        tokens.add(hex.toString());
        phones = tokens.size();
        return out.put("ok", true).put("token", hex.toString());
    }

    private static JSONObject state() throws Exception {
        Activity a = GlowApp.front();
        String screen = a == null ? "other" : a instanceof PlayerActivity ? "player" : a instanceof SearchActivity ? "search"
                : a instanceof LoginActivity ? "login" : a instanceof ChannelsActivity ? "channels" : a instanceof HomeActivity ? "home" : "glow";
        String playing = PlayerActivity.nowPlaying == null ? "" : PlayerActivity.nowPlaying;
        Channel ch = PlayerActivity.nowChannel;
        Guide.Prog p = ch == null ? null : Guide.now(ch);
        if (p != null) playing += " — " + p.title;
        return new JSONObject().put("screen", screen).put("playing", playing);
    }

    private static List<Channel> allChannels() {
        List<Channel> all = new ArrayList<>(FreeTv.cached(app));
        if (Xtream.load(app) != null) all.addAll(Xtream.cached(app));
        return all;
    }

    private static JSONObject channelJson(Channel ch) throws Exception {
        return new JSONObject().put("key", ch.key()).put("name", ch.name).put("logo", ch.logo).put("num", GuideLineup.numberOf(ch))
                .put("country", Channel.countryName(ch.country) == null ? "" : Channel.countryName(ch.country))
                .put("group", ch.group == null ? "" : ch.group).put("fav", Picks.isFavourite(app, ch));
    }

    private static JSONArray search(String q) throws Exception {
        String needle = q.trim().toLowerCase(Locale.ROOT);
        JSONArray out = new JSONArray();
        if (needle.isEmpty()) return out;
        Set<String> seen = new HashSet<>();
        List<Channel> starts = new ArrayList<>(), contains = new ArrayList<>();
        for (Channel ch : allChannels()) {
            String n = ch.name.toLowerCase(Locale.ROOT);
            int at = n.indexOf(needle);
            if (at < 0 || Channel.isOffline(app, ch) || !seen.add(ch.key())) continue;
            (at == 0 ? starts : contains).add(ch);
        }
        starts.addAll(contains);
        for (int i = 0; i < Math.min(40, starts.size()); i++) out.put(channelJson(starts.get(i)));
        return out;
    }

    private static JSONArray favourites() throws Exception {
        JSONArray out = new JSONArray();
        for (Channel ch : Picks.favourites(app, allChannels())) out.put(channelJson(ch));
        return out;
    }

    /** Plays a channel on the TV; the list it came from becomes the ▲▼ zapping order. */
    private static JSONObject play(JSONObject in) throws Exception {
        Map<String, Channel> byKey = new HashMap<>();
        for (Channel ch : allChannels()) byKey.put(ch.key(), ch);
        List<Channel> list = new ArrayList<>();
        JSONArray keys = in.optJSONArray("list");
        if (keys != null) for (int i = 0; i < keys.length(); i++) {
            Channel ch = byKey.get(keys.getString(i));
            if (ch != null) list.add(ch);
        }
        Channel chosen = byKey.get(in.optString("key"));
        if (chosen == null) return new JSONObject().put("ok", false).put("error", "Channel not found");
        if (!list.contains(chosen)) list.add(0, chosen);
        final int index = list.indexOf(chosen);
        Xtream.playlist = list;
        Net.MAIN.post(() -> {
            Intent i = new Intent(app, PlayerActivity.class).putExtra("index", index)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Activity a = GlowApp.front();
            if (a instanceof PlayerActivity) a.finish();
            app.startActivity(i);
        });
        return new JSONObject().put("ok", true).put("playing", chosen.name);
    }

    private static final Map<String, Integer> KEYS = new HashMap<>();

    static {
        KEYS.put("up", KeyEvent.KEYCODE_DPAD_UP);
        KEYS.put("down", KeyEvent.KEYCODE_DPAD_DOWN);
        KEYS.put("left", KeyEvent.KEYCODE_DPAD_LEFT);
        KEYS.put("right", KeyEvent.KEYCODE_DPAD_RIGHT);
        KEYS.put("ok", KeyEvent.KEYCODE_DPAD_CENTER);
        KEYS.put("back", KeyEvent.KEYCODE_BACK);
        KEYS.put("chup", KeyEvent.KEYCODE_CHANNEL_UP);
        KEYS.put("chdown", KeyEvent.KEYCODE_CHANNEL_DOWN);
        KEYS.put("menu", KeyEvent.KEYCODE_MENU);
        KEYS.put("last", KeyEvent.KEYCODE_LAST_CHANNEL);
        KEYS.put("red", KeyEvent.KEYCODE_PROG_RED);
        KEYS.put("green", KeyEvent.KEYCODE_PROG_GREEN);
        KEYS.put("yellow", KeyEvent.KEYCODE_PROG_YELLOW);
        KEYS.put("blue", KeyEvent.KEYCODE_PROG_BLUE);
        for (int d = 0; d <= 9; d++) KEYS.put(String.valueOf(d), KeyEvent.KEYCODE_0 + d);
    }

    /** Presses a remote key in Glow. Home works everywhere; other keys only while a Glow screen is in front. */
    private static JSONObject key(String name) throws Exception {
        if (name.equals("guide")) { // opens from anywhere, like Home
            Net.MAIN.post(() -> app.startActivity(new Intent(app, ClassicGuideActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            return new JSONObject().put("ok", true);
        }
        if (name.equals("home")) {
            Net.MAIN.post(() -> app.startActivity(new Intent(app, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            return new JSONObject().put("ok", true);
        }
        Integer code = KEYS.get(name);
        if (code == null) return new JSONObject().put("ok", false).put("error", "Unknown key");
        if (GlowApp.front() == null) return new JSONObject().put("ok", false).put("error", "Keys work while Glow is on screen. Press Home first.");
        try {
            new Instrumentation().sendKeyDownUpSync(code);
            return new JSONObject().put("ok", true);
        } catch (SecurityException e) {
            return new JSONObject().put("ok", false).put("error", "Keys work while Glow is on screen. Press Home first.");
        }
    }

    /** Types into whatever Glow text box is on screen; anywhere else it opens Search with the text. */
    private static JSONObject type(String text, boolean submit) throws Exception {
        Net.MAIN.post(() -> {
            Activity a = GlowApp.front();
            if (a instanceof SearchActivity) ((SearchActivity) a).typeFromPhone(text);
            else if (a instanceof LoginActivity) ((LoginActivity) a).typeFromPhone(text, submit);
            else app.startActivity(new Intent(app, SearchActivity.class).putExtra("query", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        return new JSONObject().put("ok", true);
    }

    private static JSONObject fav(String key) throws Exception {
        for (Channel ch : allChannels()) {
            if (ch.key().equals(key)) return new JSONObject().put("ok", true).put("fav", Picks.toggleFavourite(app, ch));
        }
        return new JSONObject().put("ok", false);
    }
}
