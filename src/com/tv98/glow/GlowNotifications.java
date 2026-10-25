package com.tv98.glow;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Notifications from the box's other apps as a small banner over whatever's on, and a list in Glow ▸ Settings ▸
 * Notifications. Needs notification access, which the viewer grants. Nothing is stored beyond the last 30, in memory.
 */
public class GlowNotifications extends NotificationListenerService {
    static final List<String[]> recent = new ArrayList<>(); // {time, app, title, text}

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (sbn.getPackageName().equals(getPackageName()) || sbn.isOngoing() || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle x = n.extras;
        CharSequence title = x.getCharSequence(Notification.EXTRA_TITLE), text = x.getCharSequence(Notification.EXTRA_TEXT);
        if (title == null && text == null) return;
        String app;
        try {
            app = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(sbn.getPackageName(), 0)).toString();
        } catch (Exception e) {
            app = sbn.getPackageName();
        }
        final String[] item = {String.valueOf(sbn.getPostTime()), app, title == null ? "" : title.toString(), text == null ? "" : text.toString()};
        Net.MAIN.post(() -> {
            synchronized (recent) {
                recent.add(0, item);
                while (recent.size() > 30) recent.remove(recent.size() - 1);
            }
            if (getSharedPreferences("display", MODE_PRIVATE).getBoolean("notify_banners", true)) {
                Overlay.show(this, item[1].toUpperCase(java.util.Locale.ROOT), item[2], item[3], 0xFF2C5FB8);
            }
        });
    }

    static boolean allowed(android.content.Context c) {
        String on = android.provider.Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return on != null && on.contains(c.getPackageName() + "/");
    }
}
