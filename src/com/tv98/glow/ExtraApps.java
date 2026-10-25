package com.tv98.glow;

import android.content.Context;
import android.content.pm.PackageInfo;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Open-source apps the viewer can choose to install, each from its developers' own official releases (GitHub, or
 * Kodi's mirror). Nothing is installed unless the viewer picks it; Android asks for confirmation as with any install.
 */
final class ExtraApps {
    /** {name, package, what it is, source (GitHub "owner/repo" or a Kodi mirror URL), asset pattern, licence}. */
    static final String[][] APPS = {
            {"SmartTube", "com.teamsmart.videomanager.tv", "YouTube for TV without ads, with SponsorBlock",
                    "yuliskov/SmartTube", "_armeabi-v7a.apk", "GPL-3.0"},
            {"Jellyfin", "org.jellyfin.androidtv", "Your own films and shows from a Jellyfin server at home",
                    "jellyfin/jellyfin-androidtv", "-release.apk", "GPL-2.0"},
            {"Kodi", "org.xbmc.kodi", "The classic media centre: local files, network shares, add-ons",
                    "https://mirrors.kodi.tv/releases/android/arm/", "-armeabi-v7a.apk", "GPL-2.0"},
            {"NewPipe", "org.schabi.newpipe", "Lightweight YouTube and PeerTube player (phone-style layout)",
                    "TeamNewPipe/NewPipe", ".apk", "GPL-3.0"},
    };

    /** What a release offers: where to download it, its version and size. */
    static final class Release {
        String url, version;
        long size;
    }

    private ExtraApps() {}

    /** Installed version name, or null. */
    static String installed(Context c, String pkg) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(pkg, 0);
            return p.versionName == null ? "" : p.versionName;
        } catch (Exception e) {
            return null;
        }
    }

    /** Off the main thread: the latest official build for this box (32-bit ARM), or null. */
    static Release latest(String[] app) throws Exception {
        String source = app[3], pattern = app[4];
        Release r = new Release();
        if (source.startsWith("https://")) { // Kodi's mirror: the newest stable armeabi-v7a build in the listing
            String html = Net.text(source);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("href=\"(kodi-([0-9.]+)-[A-Za-z]+" + java.util.regex.Pattern.quote(pattern) + ")\"").matcher(html);
            String best = null, bestV = null;
            while (m.find()) {
                if (bestV == null || compare(m.group(2), bestV) > 0) {
                    best = m.group(1);
                    bestV = m.group(2);
                }
            }
            if (best == null) return null;
            r.url = source + best;
            r.version = bestV;
            r.size = -1;
            return r;
        }
        JSONObject rel = new JSONObject(Net.text("https://api.github.com/repos/" + source + "/releases/latest"));
        r.version = rel.optString("tag_name");
        JSONArray assets = rel.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            String name = a.optString("name");
            if (name.endsWith(pattern) && !name.contains("debug")) {
                r.url = a.optString("browser_download_url");
                r.size = a.optLong("size", -1);
                return r;
            }
        }
        return null;
    }

    /** 21.3 vs 21.10: numeric, part by part. */
    private static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0, q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }
}
