package com.tv98.glow;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Remote buttons the viewer has given a job (Glow ▸ Settings ▸ Remote buttons): a press and, optionally, a hold.
 * They work while Glow is on screen; with the "Glow remote buttons" accessibility service switched on (by the viewer,
 * in Android's settings) they work in every app.
 */
final class Buttons {
    /** {action, label} for Glow's own places; apps are added as "app:<package>". */
    static final String[][] GLOW_ACTIONS = {
            {"glow:quick", "Quick settings"},
            {"glow:guide", "TV Guide"},
            {"glow:last", "Last channel watched"},
            {"glow:sports", "Sports now"},
            {"glow:map", "World map"},
            {"glow:search", "Search"},
            {"glow:home", "Glow Home"},
    };

    private Buttons() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("buttons", Context.MODE_PRIVATE);
    }

    /** Buttons Glow itself needs (moving around, numbers, back, volume…) can't be given another job. */
    static boolean assignable(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_CHANNEL_UP: case KeyEvent.KEYCODE_CHANNEL_DOWN: case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_PROG_BLUE: // the box's firmware takes it (opens its file manager)
            case KeyEvent.KEYCODE_UNKNOWN:
                return false;
            default:
                return !(code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9);
        }
    }

    static String action(Context c, int code, boolean held) {
        return prefs(c).getString((held ? "hold_" : "press_") + code, null);
    }

    static boolean mapped(Context c, int code) {
        return action(c, code, false) != null || action(c, code, true) != null;
    }

    static void set(Context c, int code, String press, String hold) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (press == null) e.remove("press_" + code); else e.putString("press_" + code, press);
        if (hold == null) e.remove("hold_" + code); else e.putString("hold_" + code, hold);
        e.apply();
    }

    static List<Integer> all(Context c) {
        List<Integer> out = new ArrayList<>();
        for (String k : prefs(c).getAll().keySet()) {
            int code = Integer.parseInt(k.substring(k.indexOf('_') + 1));
            if (!out.contains(code)) out.add(code);
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** "Prog red", "Media play pause", "F1"… */
    static String keyName(int code) {
        String n = KeyEvent.keyCodeToString(code).replace("KEYCODE_", "").replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return n.isEmpty() ? "Button " + code : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    static String label(Context c, String action) {
        if (action == null) return "nothing";
        for (String[] a : GLOW_ACTIONS) if (a[0].equals(action)) return a[1];
        if (action.startsWith("app:")) {
            try {
                return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(action.substring(4), 0)).toString();
            } catch (Exception e) {
                return action.substring(4);
            }
        }
        return action;
    }

    /** Glow's places, then every app on the box. */
    static void choices(Context c, List<String> actions, List<String> labels) {
        for (String[] a : GLOW_ACTIONS) {
            actions.add(a[0]);
            labels.add(a[1]);
        }
        for (ResolveInfo ri : AppsActivity.ordered(c, true)) {
            actions.add("app:" + ri.activityInfo.packageName);
            labels.add("App: " + ri.loadLabel(c.getPackageManager()));
        }
    }

    /** Does the button's job. Works from any context (the accessibility service has no activity). */
    static void run(final Context c, String action) {
        if (action == null) return;
        Intent i = null;
        switch (action) {
            case "glow:quick": i = new Intent(c, QuickSettingsActivity.class); break;
            case "glow:guide": i = new Intent(c, ClassicGuideActivity.class); break;
            case "glow:sports": i = new Intent(c, IntelActivity.class).putExtra(IntelActivity.EXTRA_SECTION, IntelActivity.SPORTS); break;
            case "glow:map": i = new Intent(c, IntelActivity.class).putExtra(IntelActivity.EXTRA_SECTION, IntelActivity.MAP); break;
            case "glow:search": i = new Intent(c, SearchActivity.class); break;
            case "glow:home": i = new Intent(c, HomeActivity.class); break;
            case "glow:last":
                Net.IO.execute(() -> {
                    List<Channel> recent = Channel.recent(c, FreeTv.cached(c));
                    if (recent.isEmpty()) return;
                    Net.MAIN.post(() -> {
                        Xtream.playlist = new ArrayList<>(recent);
                        c.startActivity(new Intent(c, PlayerActivity.class).putExtra("index", 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    });
                });
                return;
            default:
                if (action.startsWith("app:")) {
                    String pkg = action.substring(4);
                    i = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                    if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(pkg);
                }
        }
        if (i == null) return;
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    /** True while the viewer has switched on "Glow remote buttons" in Android's accessibility settings. */
    static boolean everywhere(Context c) {
        String on = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return on != null && on.contains(c.getPackageName() + "/");
    }

    /** In-app handling (Home, the player): press and hold, for activities to call from their key methods. */
    static final class InApp {
        private boolean held;

        boolean down(Context c, KeyEvent e) {
            if (!mapped(c, e.getKeyCode())) return false;
            if (e.getRepeatCount() == 0) {
                held = false;
                e.startTracking();
            }
            return true;
        }

        boolean longPress(Context c, int code) {
            if (!mapped(c, code)) return false;
            String a = action(c, code, true);
            if (a == null) return false;
            held = true;
            run(c, a);
            return true;
        }

        boolean up(Context c, KeyEvent e) {
            if (!mapped(c, e.getKeyCode())) return false;
            if (!held && !e.isCanceled()) run(c, action(c, e.getKeyCode(), false));
            held = false;
            return true;
        }
    }
}
