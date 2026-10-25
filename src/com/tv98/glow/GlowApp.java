package com.tv98.glow;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

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

    @Override
    public void onCreate() {
        super.onCreate();
        Reminders.start(this);
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
