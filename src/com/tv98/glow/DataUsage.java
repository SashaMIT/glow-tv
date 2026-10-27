package com.tv98.glow;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.TrafficStats;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Data the box has used, today and this month. Android only counts since the box last started, so Glow adds up the
 * counter's growth every 15 minutes (and when the Network page opens) into daily totals, kept for 40 days.
 * Per app would need Android's usage access for Glow, which isn't switched on.
 */
final class DataUsage {
    private DataUsage() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("data", Context.MODE_PRIVATE);
    }

    private static String day(long t) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(t));
    }

    /** Adds what's been used since the last look to today's total. */
    static synchronized void sample(Context c) {
        long total = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
        if (total < 0) return; // not supported
        SharedPreferences p = prefs(c);
        long last = p.getLong("last", -1);
        // The counter starts again from 0 when the box restarts.
        long used = last < 0 ? 0 : total >= last ? total - last : total;
        String today = "d" + day(System.currentTimeMillis());
        SharedPreferences.Editor e = p.edit().putLong("last", total).putLong(today, p.getLong(today, 0) + used);
        long cutoff = System.currentTimeMillis() - 40L * 86_400_000L;
        for (String k : p.getAll().keySet()) {
            if (k.startsWith("d") && k.length() == 11 && k.substring(1).compareTo(day(cutoff)) < 0) e.remove(k);
        }
        e.apply();
    }

    static long today(Context c) {
        return prefs(c).getLong("d" + day(System.currentTimeMillis()), 0);
    }

    static long thisMonth(Context c) {
        String month = "d" + day(System.currentTimeMillis()).substring(0, 7);
        long n = 0;
        for (java.util.Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            if (e.getKey().startsWith(month) && e.getValue() instanceof Long) n += (Long) e.getValue();
        }
        return n;
    }

    /** Since this box last started, and Glow's own part of it. */
    static long sinceStart() {
        return Math.max(0, TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes());
    }

    static long glow() {
        int uid = android.os.Process.myUid();
        return Math.max(0, TrafficStats.getUidRxBytes(uid) + TrafficStats.getUidTxBytes(uid));
    }

    /** "1.4 GB", "820 MB". */
    static String size(long bytes) {
        if (bytes >= 1L << 30) return String.format(Locale.ROOT, "%.1f GB", bytes / (double) (1L << 30));
        return String.format(Locale.ROOT, "%d MB", bytes >> 20);
    }
}
