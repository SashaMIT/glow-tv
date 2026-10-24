package com.tv98.glow;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Official extra sources for well-known news channels, from World Monitor's catalogue ({@link WmCatalog}):
 * the broadcasters' own stream links, and their YouTube live stream when one is on air right now
 * (World Monitor's public "liveVideoResolved" feed, refreshed every 10 minutes).
 */
final class OfficialLive {
    private static final String LIVE_URL = "https://api.worldmonitor.app/api/bootstrap?keys=liveVideoResolved&public=1";
    private static final String FILE = "wm_live.json";
    private static final long FRESH_MS = 10 * 60 * 1000L;
    /** Names that don't line up with the catalogue by prefix. */
    private static final String[][] ALIASES = {{"cnainternational", "CNA (NewsAsia)"}, {"cnasingapore", "CNA (NewsAsia)"}};

    /**
     * Official YouTube channels of Thai broadcasters (verified badge / millions of subscribers, checked 24 Oct 2026).
     * {lower-case Glow channel name, channel ids in preference order}. Several only go live at certain hours.
     */
    private static final String[][] THAI = {
            {"3hd", "UC48h7Dst_hX82HxOf3xJw_w", "UCirZPTc9IoKM_DsA9aKbc4g"},
            {"channel 7 (thailand)", "UCXUVnTEsLZBim_WlWxBvEwA", "UC2OtDM92rLjt4mm43ED1Q-w"},
            {"workpoint tv", "UC3ZkCd7XtUREnjjt3cyY_gg", "UC3WyfUir0HD8sFI4AVAl6SQ"},
            {"one 31", "UC8f7MkX4MFOOJ2SerXLInCA", "UCqZ3is1Z4ck-I0ObYFw8OEQ"},
            {"thairath tv", "UCrFDdD-EE05N7gjwZho2wqw"},
            {"pptv", "UC7FCQJFK1sfwD_uobB45Xng"},
            {"nation tv", "UCJ6PZBK3kOYKBLmvKwdI1gg", "UCIoFfVIOrRRbI-WVdDhTTwg"},
            {"tnn16", "UCqUBA96OsqMgSFvTwLXY9yw"},
            {"thai pbs", "UC5TOFhyb_LxL2VG_Zenhpzw", "UCOFvLl4bKwCIZg0r4EBQLug"},
            {"gmm 25", "UCTP5z0kFg6-nPcZ79gTC67Q"},
            {"mcot hd", "UC59st8OON3dV50gHefqUlsg"},
            {"channel 8 (thailand)", "UCtBu8Wb2BUoduUXJS9Uss7Q", "UC6x41swVZP3rEmy-ODxLMFA"},
            {"true4u", "UCu2sn1JnlYkN_XQCeBcKP5A"},
            {"top news", "UCeF5sxjXSdWq80n3RA9gBpw", "UCneS5JrD0vl6dhqs7FCZKCA"},
            {"jkn 18", "UC95SaPHGP1Py_V96Fkj_4tQ"},
            {"nbt 2 hd", "UCl7n5rLxw3_p30faqfXh9qg", "UCwlP2cdknwTQueF3x866CEg"},
            {"mono 29", "UCRcxIbPtL9BX4NJzP6LE7Yw"},
    };
    private static final long PAGE_FRESH_MS = 10 * 60 * 1000L;
    private static final Map<String, String[]> pageCache = new HashMap<>(); // channel id -> {videoId or "", time}

    private static Map<String, String> liveByChannel; // YouTube channel id -> live video id
    private static long liveLoaded;

    private OfficialLive() {}

    private static String norm(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) if (Character.isLetterOrDigit(c)) b.append(c);
        return b.toString();
    }

    /** Catalogue names, normalised once (match() runs for every channel in every list). */
    private static final String[] NORM = new String[WmCatalog.CHANNELS.length];

    static {
        for (int i = 0; i < NORM.length; i++) NORM[i] = norm(WmCatalog.CHANNELS[i][0]);
    }

    /** The catalogue row for this channel: exact name, alias, else the longest catalogue name it starts with. */
    static String[] match(Channel ch) {
        String n = norm(ch.name);
        for (String[] a : ALIASES) {
            if (n.startsWith(a[0])) {
                for (String[] row : WmCatalog.CHANNELS) if (row[0].equals(a[1])) return row;
            }
        }
        String[] best = null;
        int bestLen = 0;
        for (int i = 0; i < NORM.length; i++) {
            String[] row = WmCatalog.CHANNELS[i];
            String r = NORM[i];
            if (r.length() < 3 || !(n.startsWith(r))) continue; // cheap test first
            if (!sameRegion(ch.country, row[1])) continue; // "ABC News" (Australia) is not ABC News (US)
            if (n.equals(r)) return row;
            if (n.startsWith(r) && r.length() > bestLen && r.length() >= 4) {
                best = row;
                bestLen = r.length();
            }
        }
        return best;
    }

    static List<String> streams(String[] row) {
        List<String> out = new ArrayList<>();
        for (int i = 3; i < row.length; i++) out.add(row[i]);
        return out;
    }

    private static final String[][] REGIONS = {
            {"na", "us ca"},
            {"latam", "mx br ar co cl pe ve ec uy py bo cr pa gt hn sv ni do cu pr"},
            {"eu", "uk gb ie fr de es it pt nl be lu ch at pl se no dk fi is gr tr ru ua by ro hu cz sk bg rs hr si ba mk al me ee lv lt md ge am az cy mt"},
            {"me", "ae sa qa il ps ir iq eg lb jo kw bh om sy ye"},
            {"asia", "jp kr kp cn tw hk mo in pk bd lk np sg my th vn id ph kh la mm kz uz"},
            {"africa", "za ng ke gh et ma dz tn ug tz sn ci cm zw zm ao"},
            {"oc", "au nz fj pg"},
    };

    /** Unknown country or region: allowed. Otherwise the channel's country must sit in the row's region. */
    private static boolean sameRegion(String country, String region) {
        if (country == null || region == null || region.isEmpty()) return true;
        for (String[] r : REGIONS) {
            if ((" " + r[1] + " ").contains(" " + country + " ")) return r[0].equals(region);
        }
        return true;
    }

    /** Official YouTube channels for this channel, best first (news catalogue, then Thai broadcasters). */
    static List<String> youtubeChannels(Channel ch) {
        List<String> out = new ArrayList<>();
        String[] row = match(ch);
        if (row != null && !row[2].isEmpty()) out.add(row[2]);
        String n = ch.name.toLowerCase(Locale.ROOT).trim();
        for (String[] t : THAI) {
            if (t[0].equals(n)) for (int i = 1; i < t.length; i++) if (!out.contains(t[i])) out.add(t[i]);
        }
        return out;
    }

    /**
     * Current live video id from the first of these channels that's on air, or null. Uses World Monitor's
     * feed when it covers the channel, otherwise reads the channel's /live page. Call off the main thread.
     */
    static String resolve(Context c, List<String> channelIds) {
        Map<String, String> wm = live(c);
        for (String id : channelIds) {
            String v = wm.get(id);
            if (v != null) return v;
            v = fromPage(id);
            if (v != null) return v;
        }
        return null;
    }

    /** Reads youtube.com/channel/<id>/live only as far as needed to see the live video id (~0.9 MB). */
    private static String fromPage(String channelId) {
        synchronized (pageCache) {
            String[] hit = pageCache.get(channelId);
            if (hit != null && System.currentTimeMillis() - Long.parseLong(hit[1]) < PAGE_FRESH_MS) {
                return hit[0].isEmpty() ? null : hit[0];
            }
        }
        String video = null;
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL("https://www.youtube.com/channel/" + channelId + "/live").openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
            conn.setRequestProperty("Accept-Language", "en-US,en");
            conn.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI");
            StringBuilder page = new StringBuilder(1 << 20);
            byte[] buf = new byte[32 * 1024];
            String canonical = null;
            try (java.io.InputStream in = conn.getInputStream()) {
                int n, scanned = 0;
                while ((n = in.read(buf)) > 0 && page.length() < 2_000_000) {
                    page.append(new String(buf, 0, n, "UTF-8"));
                    if (canonical == null) {
                        int at = page.indexOf("<link rel=\"canonical\" href=\"https://www.youtube.com/watch?v=");
                        if (at >= 0 && page.length() > at + 80) {
                            int start = page.indexOf("v=", at) + 2;
                            canonical = page.substring(start, start + 11);
                        }
                    }
                    if (canonical != null && page.indexOf("\"isLive\":true", Math.max(0, scanned - 20)) >= 0) {
                        video = canonical;
                        break;
                    }
                    scanned = page.length();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        synchronized (pageCache) {
            pageCache.put(channelId, new String[] {video == null ? "" : video, String.valueOf(System.currentTimeMillis())});
        }
        return video;
    }

    private static synchronized Map<String, String> live(Context c) {
        long now = System.currentTimeMillis();
        if (liveByChannel != null && now - liveLoaded < FRESH_MS) return liveByChannel;
        File f = new File(c.getFilesDir(), FILE);
        String body = null;
        if (!f.exists() || now - f.lastModified() > FRESH_MS) {
            try {
                byte[] b = Net.bytes(LIVE_URL, 512 * 1024);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(b);
                }
                body = new String(b, "UTF-8");
            } catch (Exception ignored) {
            }
        }
        if (body == null && f.exists()) {
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] b = new byte[(int) f.length()];
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
                body = new String(b, "UTF-8");
            } catch (Exception ignored) {
            }
        }
        Map<String, String> map = new HashMap<>();
        try {
            JSONObject channels = new JSONObject(body).getJSONObject("data")
                    .getJSONObject("liveVideoResolved").getJSONObject("channels");
            for (Iterator<String> it = channels.keys(); it.hasNext(); ) {
                String id = it.next();
                String video = channels.getJSONObject(id).optString("videoId");
                if (!video.isEmpty()) map.put(id, video);
            }
        } catch (Exception ignored) {
        }
        liveByChannel = map;
        liveLoaded = now;
        return map;
    }
}
