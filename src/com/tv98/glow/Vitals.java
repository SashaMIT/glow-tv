package com.tv98.glow;

import android.content.Context;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The box's black box. Temperature and uptime every 5 minutes, so a restart leaves evidence of how hot it was just
 * before (on 26 Oct the box restarted itself at 80-82 °C and left no record of any kind), and every crash's stack.
 * The temperature also tells the rest of Glow to go easy: see {@link #hot()}.
 */
final class Vitals {
    /** From here the box gets close to where it has restarted itself: optional work waits. */
    static final int HOT = 80;
    private static final String LOG = "vitals.txt", CRASHES = "crashes.txt";
    private static final int KEEP_LINES = 300; // 25 hours
    private static final int KEEP_CRASH_BYTES = 16 * 1024;
    private static final File TEMP = new File("/sys/class/thermal/thermal_zone0/temp");

    private static volatile int degrees = -1;
    private static int ticks;

    private Vitals() {}

    /** Every process: crashes are written down before Android ends the process. */
    static void recordCrashes(Context c) {
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler next = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter w = new StringWriter();
                e.printStackTrace(new PrintWriter(w));
                File f = new File(app.getFilesDir(), CRASHES);
                String entry = System.currentTimeMillis() / 1000 + " " + app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName
                        + " " + android.app.Application.getProcessName() + " " + t.getName() + "\n" + w + "\n";
                String all = (f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "") + entry;
                if (all.length() > KEEP_CRASH_BYTES) all = all.substring(all.length() - KEEP_CRASH_BYTES);
                Files.write(f.toPath(), all.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
            if (next != null) next.uncaughtException(t, e);
        });
    }

    /** The main process, once a minute: the temperature now, written down every fifth time (from the first). */
    static void tick(Context c) {
        degrees = readDegrees();
        if (ticks++ % 5 != 0 || degrees < 0) return;
        try {
            File f = new File(c.getFilesDir(), LOG);
            String line = System.currentTimeMillis() / 1000 + " " + SystemClock.elapsedRealtime() / 1000 + " " + degrees + "\n";
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
            if (f.length() > KEEP_LINES * 2 * 28L) { // about twice what's kept: cut it back to the newest
                List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                Files.write(f.toPath(), lines.subList(Math.max(0, lines.size() - KEEP_LINES), lines.size()), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
    }

    /** °C, or -1 when this box doesn't say. */
    static int readDegrees() {
        try {
            String s = new String(Files.readAllBytes(TEMP.toPath()), StandardCharsets.US_ASCII).trim();
            int v = Integer.parseInt(s);
            return v > 1000 ? v / 1000 : v;
        } catch (Exception e) {
            return -1;
        }
    }

    /** True when the box is hot: leave out what's optional (standby players, link checks). */
    static boolean hot() {
        return degrees >= HOT;
    }

    /** What the black box shows. */
    static final class Summary {
        int now = -1, hottest = -1, beforeRestart = -1;
        long restartAt;
    }

    /** From the log's lines ("epoch uptime degrees"): the hottest in the last 24 h, and the latest restart. */
    static Summary summarize(List<String> lines, long nowSeconds) {
        Summary s = new Summary();
        long lastUptime = -1;
        int lastDegrees = -1;
        for (String l : lines) {
            String[] p = l.trim().split(" ");
            if (p.length != 3) continue;
            try {
                long at = Long.parseLong(p[0]), up = Long.parseLong(p[1]);
                int d = Integer.parseInt(p[2]);
                if (lastUptime >= 0 && up < lastUptime) { // the clock since boot went back: the box restarted
                    s.restartAt = at - up;
                    s.beforeRestart = lastDegrees;
                }
                if (nowSeconds - at <= 86_400) s.hottest = Math.max(s.hottest, d);
                s.now = d;
                lastUptime = up;
                lastDegrees = d;
            } catch (NumberFormatException ignored) {
            }
        }
        return s;
    }

    static Summary summary(Context c) {
        List<String> lines = new ArrayList<>();
        try {
            lines = Files.readAllLines(new File(c.getFilesDir(), LOG).toPath(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        Summary s = summarize(lines, System.currentTimeMillis() / 1000);
        int d = readDegrees();
        if (d >= 0) s.now = d;
        return s;
    }

    /** Number of crashes written down: each entry starts with a line of "epoch version process thread". */
    static int countCrashes(String text) {
        int n = 0;
        for (String l : text.split("\n")) {
            String[] p = l.split(" ");
            if (p.length == 4 && p[0].length() >= 9 && p[0].chars().allMatch(Character::isDigit)) n++;
        }
        return n;
    }

    static String crashes(Context c) {
        try {
            return new String(Files.readAllBytes(new File(c.getFilesDir(), CRASHES).toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** "Now 68 °C · hottest today 79 °C · no crashes · last restart 5 Nov, 14:02 (81 °C before it)". */
    static String line(Context c) {
        Summary s = summary(c);
        int crashes = countCrashes(crashes(c));
        List<String> parts = new ArrayList<>();
        if (s.now >= 0) parts.add(L.f("now {n} °C", s.now));
        if (s.hottest >= 0) parts.add(L.f("hottest today {n} °C", s.hottest));
        parts.add(crashes == 0 ? L.t("no crashes") : L.f("crashes: {n}", crashes));
        if (s.restartAt > 0) {
            String when = android.text.format.DateFormat.format(Ui.is24h(c) ? "d MMM, HH:mm" : "d MMM, h:mm a", s.restartAt * 1000).toString();
            parts.add(L.t("last restart") + " " + when + (s.beforeRestart >= 0 ? " " + L.f("({n} °C before it)", s.beforeRestart) : ""));
        }
        return String.join("   ·   ", parts);
    }
}
