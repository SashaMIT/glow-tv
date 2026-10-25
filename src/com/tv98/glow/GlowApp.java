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
    private final Runnable alertsEverywhere = new Runnable() {
        @Override
        public void run() {
            Net.MAIN.postDelayed(this, 60_000);
            if (!Overlay.allowed(GlowApp.this) || !ScoreAlerts.enabled(GlowApp.this) || front instanceof PlayerActivity) return;
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
        Reminders.start(this);
        Net.MAIN.postDelayed(alertsEverywhere, 90_000);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) { front = a; }
            @Override public void onActivityPaused(Activity a) { if (front == a) front = null; }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }
}
