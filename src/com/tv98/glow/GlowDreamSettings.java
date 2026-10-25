package com.tv98.glow;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/** Turns Glow's screensaver on (15 minutes idle) or off, remembering and restoring the box's own settings. */
final class GlowDreamSettings {
    static final String COMPONENT = "com.tv98.glow/com.tv98.glow.GlowDream";
    private static final int IDLE_MS = 15 * 60 * 1000;

    private GlowDreamSettings() {}

    static boolean on(Context c) {
        String cur = Settings.Secure.getString(c.getContentResolver(), "screensaver_components");
        return cur != null && cur.contains("com.tv98.glow");
    }

    /** Returns null when done, else a message for the viewer. */
    static String set(Context c, boolean enable) {
        ContentResolver cr = c.getContentResolver();
        SharedPreferences p = c.getSharedPreferences("dream", Context.MODE_PRIVATE);
        try {
            if (enable) {
                if (!p.contains("prev_components")) {
                    p.edit().putString("prev_components", Settings.Secure.getString(cr, "screensaver_components"))
                            .putInt("prev_timeout", Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, Integer.MAX_VALUE - 1))
                            .apply();
                }
                Settings.Secure.putString(cr, "screensaver_components", COMPONENT);
                Settings.Secure.putInt(cr, "screensaver_enabled", 1);
                Settings.Secure.putInt(cr, "screensaver_activate_on_sleep", 1);
                Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, IDLE_MS);
            } else {
                Settings.Secure.putString(cr, "screensaver_components", p.getString("prev_components", null));
                Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, p.getInt("prev_timeout", Integer.MAX_VALUE - 1));
                p.edit().clear().apply();
            }
            return null;
        } catch (SecurityException e) {
            return "Glow needs a one-time permission for this (box/make-glow-home.sh grants it).";
        }
    }
}
