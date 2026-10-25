package com.tv98.glow;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;

/**
 * Which webcam streams are still live, judged from their YouTube thumbnails alone (no API, no watch pages):
 * a live stream's frame changes between looks; one that has ended keeps the same picture, and a removed one has
 * no picture (404) or a grey placeholder. Checked every 6 hours (~330 KB); an ended stream is skipped for a day
 * and the cam's next stream in World Monitor's list is used.
 */
final class Cams {
    private static final long EVERY = 6 * 3_600_000L, RETRY = 24 * 3_600_000L;
    private static final int PLACEHOLDER_BYTES = 2000; // YouTube's grey "no thumbnail" is ~1.1–1.4 KB

    private Cams() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cams", Context.MODE_PRIVATE);
    }

    private static boolean dead(SharedPreferences p, String id) {
        long since = p.getLong("d_" + id, 0);
        return since > 0 && System.currentTimeMillis() - since < RETRY;
    }

    /** The stream to show for a cam: its first one that isn't known to have ended. */
    static String id(Context c, int cam) {
        String[] r = WmCams.CAMS[cam];
        SharedPreferences p = prefs(c);
        for (int k = 6; k < r.length; k++) if (!dead(p, r[k])) return r[k];
        return r[6];
    }

    /** False when every stream of this cam has ended. */
    static boolean live(Context c, int cam) {
        String[] r = WmCams.CAMS[cam];
        SharedPreferences p = prefs(c);
        for (int k = 6; k < r.length; k++) if (!dead(p, r[k])) return true;
        return false;
    }

    static int liveCount(Context c) {
        int n = 0;
        for (int i = 0; i < WmCams.CAMS.length; i++) if (live(c, i)) n++;
        return n;
    }

    /** Cam indexes with the live ones first (World Monitor's order kept within each group). */
    static int[] order(Context c) {
        int[] out = new int[WmCams.CAMS.length];
        int n = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < WmCams.CAMS.length; i++) if (live(c, i) == (pass == 0)) out[n++] = i;
        }
        return out;
    }

    /** Off the main thread. Looks at each cam's current stream if the last look was 6+ hours ago. */
    static void checkIfDue(Context c) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        if (now - p.getLong("checked", 0) < EVERY || !RUNNING.compareAndSet(false, true)) return;
        int ended = 0;
        try {
            for (int i = 0; i < WmCams.CAMS.length; i++) {
                String id = id(c, i);
                String verdict = look(p, id, now);
                if (verdict != null) {
                    p.edit().putLong("d_" + id, now).apply();
                    ended++;
                    android.util.Log.i("GlowPerf", "cam " + WmCams.CAMS[i][0] + " stream " + id + " ended: " + verdict);
                }
            }
            p.edit().putLong("checked", now).apply(); // only once the whole round is done
        } finally {
            RUNNING.set(false);
        }
        android.util.Log.i("GlowPerf", "cams checked in " + (System.currentTimeMillis() - now) + " ms, " + ended + " ended");
    }

    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING = new java.util.concurrent.atomic.AtomicBoolean();

    /** Null if the stream looks live, else why not. */
    private static String look(SharedPreferences p, String id, long now) {
        byte[] b;
        try {
            b = Net.bytes("https://i.ytimg.com/vi/" + id + "/mqdefault_live.jpg", 300 * 1024);
        } catch (java.io.IOException e) {
            return String.valueOf(e.getMessage()).contains("HTTP 404") ? "no picture" : null; // offline box: no verdict
        }
        if (b.length < PLACEHOLDER_BYTES) return "placeholder picture";
        String hash = b.length + ":" + Arrays.hashCode(b);
        String before = p.getString("h_" + id, "");
        long at = p.getLong("t_" + id, 0);
        p.edit().putString("h_" + id, hash).putLong("t_" + id, now).apply();
        // Same picture as at the last look (at least an hour earlier): the stream has stopped.
        if (hash.equals(before) && now - at >= 3_600_000L) return "picture unchanged since " + ((now - at) / 60_000) + " min";
        return null;
    }
}
