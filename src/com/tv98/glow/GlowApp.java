package com.tv98.glow;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.util.List;

/** Tracks which Glow screen is in front, so the phone remote can type into it or send it keys. */
public class GlowApp extends Application {
    private static Activity front;

    static Activity front() {
        return front;
    }

    /**
     * Android asks for memory back (another app such as Netflix is in front, or memory is short): give back the
     * decoded pictures. On a 1 GB box that memory goes straight to the app being watched.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN) Net.trim();
    }

    /**
     * Score alerts while another app (Netflix, YouTube…) is on: every minute, if banners over other apps are allowed.
     * Glow's own player shows its own banners.
     */
    /** Data used, added up every 15 minutes (Android's own counter restarts with the box). */
    private final Runnable dataTick = new Runnable() {
        @Override
        public void run() {
            Net.MAIN.postDelayed(this, 15 * 60_000);
            Net.IO.execute(() -> DataUsage.sample(GlowApp.this));
        }
    };

    /** The doorbell (Home Assistant), checked every 3 s when one is set up; the check itself runs off the main thread. */
    private volatile boolean ringing;
    private final Runnable doorbell = new Runnable() {
        @Override
        public void run() {
            Net.MAIN.postDelayed(this, 3_000);
            if (ringing || !HomeAssistant.configured(GlowApp.this)) return;
            ringing = true;
            Net.IO.execute(() -> {
                try {
                    HomeAssistant.checkDoorbell(GlowApp.this);
                } finally {
                    ringing = false;
                }
            });
        }
    };

    private final Runnable alertsEverywhere = new Runnable() {
        @Override
        public void run() {
            Net.MAIN.postDelayed(this, 60_000);
            // Only while another app is in front: Glow's player has its own banners, and Home needn't do the work.
            if (front != null || !Overlay.allowed(GlowApp.this) || !ScoreAlerts.enabled(GlowApp.this)) return;
            Net.WORK.execute(() -> {
                List<ScoreAlerts.Alert> found = ScoreAlerts.check(GlowApp.this);
                Net.MAIN.post(() -> {
                    for (ScoreAlerts.Alert a : found) {
                        Overlay.show(GlowApp.this, Sports.LEAGUES[a.league][1].toUpperCase(java.util.Locale.ROOT), a.headline, a.line,
                                0xFF000000 | Integer.parseInt(Sports.LEAGUES[a.league][2], 16));
                    }
                });
            });
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        L.init(this);
        Reminders.start(this);
        Net.MAIN.postDelayed(alertsEverywhere, 90_000);
        Net.MAIN.postDelayed(doorbell, 10_000);
        Net.MAIN.post(dataTick);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) { front = a; }
            @Override public void onActivityPaused(Activity a) { if (front == a) front = null; }
            @Override public void onActivityCreated(Activity a, Bundle b) {
                // Kids mode: Home, the Kids guide and the player only; Settings asks for the PIN to leave Kids mode.
                if (Kids.on(a) && !Kids.allowed(a)) {
                    if (a instanceof SettingsActivity) PinActivity.leave(a);
                    a.finish();
                }
            }
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }
}
