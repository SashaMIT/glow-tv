package com.tv98.glow;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kids mode (Glow ▸ Settings ▸ Kids mode): Home shows only the apps a grown-up picked and the Kids channels; the TV
 * Guide opens on Kids; Search, news, Intelligence, Settings and the other screens are closed until the 4-digit PIN is
 * entered. It's a friendly fence for young children, not a lock: Android's own settings can still reset Glow.
 */
final class Kids {
    /** The screens Kids mode allows; any other Glow screen closes as it opens. */
    private static final List<Class<?>> ALLOWED = Arrays.asList(
            HomeActivity.class, ClassicGuideActivity.class, PlayerActivity.class, PinActivity.class);
    private static final int TRIES = 5;
    private static final long WAIT_MS = 60_000;

    private Kids() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("kids", Context.MODE_PRIVATE);
    }

    static boolean on(Context c) {
        return prefs(c).getBoolean("on", false);
    }

    static void setOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("on", on).commit();
        if (on && Remote.running()) Remote.stop(); // the phone remote could open anything
    }

    static boolean allowed(Activity a) {
        return ALLOWED.contains(a.getClass());
    }

    // ---- PIN: stored as a salted SHA-256, never as digits ----

    static boolean hasPin(Context c) {
        return prefs(c).contains("pin");
    }

    static void setPin(Context c, String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String s = hex(salt);
        prefs(c).edit().putString("salt", s).putString("pin", hash(s, pin)).putInt("fails", 0).apply();
    }

    /** Milliseconds until another try is allowed (after five wrong ones), or 0. */
    static long waitMs(Context c) {
        return Math.max(0, prefs(c).getLong("wait_until", 0) - System.currentTimeMillis());
    }

    static boolean check(Context c, String pin) {
        SharedPreferences p = prefs(c);
        if (waitMs(c) > 0) return false;
        boolean ok = hash(p.getString("salt", ""), pin).equals(p.getString("pin", null));
        int fails = ok ? 0 : p.getInt("fails", 0) + 1;
        SharedPreferences.Editor e = p.edit().putInt("fails", fails >= TRIES ? 0 : fails);
        if (fails >= TRIES) e.putLong("wait_until", System.currentTimeMillis() + WAIT_MS);
        e.apply();
        return ok;
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            return hex(d.digest((salt + ":" + pin).getBytes("UTF-8")));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    // ---- what children can open ----

    static Set<String> apps(Context c) {
        return new HashSet<>(prefs(c).getStringSet("apps", new HashSet<>()));
    }

    static void setApps(Context c, Set<String> pkgs) {
        prefs(c).edit().putStringSet("apps", pkgs).apply();
    }

    /** A remote-button job allowed in Kids mode: the Kids guide, Home, or a picked app. */
    static boolean allowsButton(Context c, String action) {
        if (action.equals("glow:guide") || action.equals("glow:home")) return true;
        return action.startsWith("app:") && apps(c).contains(action.substring(4));
    }

    /** The guide's Kids section: working Kids and animation channels, best first. Off the main thread. */
    static List<Channel> channels(Context c) {
        GuideLineup l = GuideLineup.build(c);
        GuideLineup.Section s = l.section(Genres.NAMES[Genres.KIDS]);
        return s == null ? new ArrayList<>() : new ArrayList<>(s.channels);
    }
}
