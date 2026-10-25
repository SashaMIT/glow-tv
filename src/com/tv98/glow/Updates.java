package com.tv98.glow;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks GitHub once a week for a newer Glow release (built by .github/workflows/release.yml) and installs it on
 * request: the APK is downloaded, then Android's own installer asks the viewer to confirm.
 */
final class Updates {
    static final String REPO = "SashaMIT/glow-tv";
    private static final long CHECK_EVERY_MS = 7L * 24 * 60 * 60 * 1000;

    private Updates() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("updates", Context.MODE_PRIVATE);
    }

    static long installedCode(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    /** "1.4.0" (or null) when a newer release than this build is known. */
    static String available(Context c) {
        SharedPreferences p = prefs(c);
        return p.getLong("code", 0) > installedCode(c) ? p.getString("name", null) : null;
    }

    /** Asks GitHub for the latest release if the last check is a week old. Call off the main thread. */
    static void checkIfDue(Context ctx) {
        Context c = ctx.getApplicationContext();
        SharedPreferences p = prefs(c);
        if (System.currentTimeMillis() - p.getLong("checked_at", 0) < CHECK_EVERY_MS) return;
        p.edit().putLong("checked_at", System.currentTimeMillis()).apply();
        try {
            JSONObject r = new JSONObject(Net.text("https://api.github.com/repos/" + REPO + "/releases/latest"));
            String tag = r.optString("tag_name", "");
            String name = tag.startsWith("v") ? tag.substring(1) : tag;
            String apk = null;
            JSONArray assets = r.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url");
                }
            }
            long code = code(name);
            if (apk != null && code > 0) p.edit().putString("name", name).putLong("code", code).putString("url", apk).apply();
            Log.i("GlowPerf", "update check: latest " + name + " (installed " + installedCode(c) + ")");
        } catch (Exception e) {
            // No release yet or no connection: try again tomorrow rather than in a week.
            p.edit().putLong("checked_at", System.currentTimeMillis() - CHECK_EVERY_MS + 24L * 60 * 60 * 1000).apply();
            Log.i("GlowPerf", "update check failed: " + e);
        }
    }

    /** "1.4.0" -> 10400, matching the workflow's VERSION_CODE. */
    static long code(String name) {
        String[] f = name.split("\\.");
        try {
            long ma = f.length > 0 ? Long.parseLong(f[0]) : 0, mi = f.length > 1 ? Long.parseLong(f[1]) : 0,
                    pa = f.length > 2 ? Long.parseLong(f[2]) : 0;
            return ma * 10000 + mi * 100 + pa;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    interface Progress {
        void say(String text);
    }

    /** Downloads the known release and hands it to Android's installer (which asks the viewer). Call off the main thread. */
    static void install(Context ctx, Progress progress) {
        Context c = ctx.getApplicationContext();
        String url = prefs(c).getString("url", null);
        if (url == null) return;
        File apk = new File(c.getCacheDir(), "glow-update.apk");
        try {
            progress.say("Downloading the update…");
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
                byte[] buf = new byte[64 * 1024];
                long total = 0;
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    total += n;
                    if (total > 40L * 1024 * 1024) throw new java.io.IOException("unexpectedly large download");
                }
            } finally {
                conn.disconnect();
            }
            progress.say("Opening the installer…");
            PackageInstaller pi = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(c.getPackageName());
            int id = pi.createSession(params);
            try (PackageInstaller.Session s = pi.openSession(id)) {
                try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("glow.apk", 0, apk.length())) {
                    byte[] buf = new byte[64 * 1024];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                    s.fsync(out);
                }
                Intent done = new Intent(c, Result.class);
                PendingIntent pending = PendingIntent.getBroadcast(c, id, done, PendingIntent.FLAG_UPDATE_CURRENT);
                s.commit(pending.getIntentSender());
            }
        } catch (Exception e) {
            progress.say("Update failed: " + e.getMessage());
        }
    }

    /** Android's installer reports back here; when it needs the viewer's OK, show its confirmation screen. */
    public static class Result extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                android.widget.Toast.makeText(c, "Update not installed: " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                        android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }
}
