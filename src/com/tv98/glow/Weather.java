package com.tv98.glow;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Locale;
import java.util.TimeZone;

/** Current temperature from Open-Meteo (free, no key), located by the box's time zone. */
final class Weather {
    interface Callback {
        void onWeather(String text);
    }

    private static final long FRESH_MS = 30 * 60 * 1000L;

    private Weather() {}

    /** Glow ▸ Settings ▸ Weather: {name, latitude, longitude}; 0 = from the box's time zone. */
    static final String[][] CITIES = {
            {"Automatic (time zone)", "", ""}, {"Bangkok", "13.75", "100.50"}, {"Chiang Mai", "18.79", "98.98"},
            {"Phuket", "7.88", "98.39"}, {"Pattaya", "12.93", "100.88"}, {"Hua Hin", "12.57", "99.96"},
            {"Singapore", "1.35", "103.82"}, {"Kuala Lumpur", "3.14", "101.69"}, {"Ho Chi Minh City", "10.82", "106.63"},
            {"Tokyo", "35.68", "139.69"}, {"Dubai", "25.20", "55.27"}, {"London", "51.51", "-0.13"},
            {"Manchester", "53.48", "-2.24"}, {"Edinburgh", "55.95", "-3.19"}, {"New York", "40.71", "-74.01"},
            {"Los Angeles", "34.05", "-118.24"}, {"Sydney", "-33.87", "151.21"}};

    /** Forgets the saved reading so the next load fetches the chosen city. */
    static void reset(Context c) {
        c.getSharedPreferences("weather", Context.MODE_PRIVATE).edit().clear().apply();
    }

    static void load(Context c, Callback cb) {
        final SharedPreferences p = c.getSharedPreferences("weather", Context.MODE_PRIVATE);
        String cachedText = p.getString("text", null);
        if (cachedText != null) cb.onWeather(cachedText);
        if (System.currentTimeMillis() - p.getLong("at", 0) < FRESH_MS) return;

        int city = c.getSharedPreferences("display", Context.MODE_PRIVATE).getInt("weather_city", 0);
        final double[] ll = city > 0 && city < CITIES.length
                ? new double[] {Double.parseDouble(CITIES[city][1]), Double.parseDouble(CITIES[city][2])}
                : where(TimeZone.getDefault().getID());
        Net.IO.execute(() -> {
            try {
                String url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.2f"
                        + "&longitude=%.2f&current=temperature_2m,weather_code&timezone=auto", ll[0], ll[1]);
                JSONObject cur = new JSONObject(Net.text(url)).getJSONObject("current");
                String t = symbol(cur.optInt("weather_code")) + "  " + Math.round(cur.optDouble("temperature_2m")) + "°";
                p.edit().putString("text", t).putLong("at", System.currentTimeMillis()).apply();
                Net.MAIN.post(() -> cb.onWeather(t));
            } catch (Exception ignored) {
            }
        });
    }

    private static double[] where(String tz) {
        switch (tz) {
            case "Asia/Bangkok": return new double[] {13.75, 100.50};
            case "Asia/Singapore": return new double[] {1.35, 103.82};
            case "Asia/Ho_Chi_Minh": return new double[] {10.82, 106.63};
            case "Asia/Kuala_Lumpur": return new double[] {3.14, 101.69};
            case "Asia/Tokyo": return new double[] {35.68, 139.69};
            case "Europe/London": return new double[] {51.51, -0.13};
            case "America/New_York": return new double[] {40.71, -74.01};
            case "America/Los_Angeles": return new double[] {34.05, -118.24};
            case "Australia/Sydney": return new double[] {-33.87, 151.21};
            default: return new double[] {13.75, 100.50};
        }
    }

    private static String symbol(int code) {
        if (code == 0) return "☀";
        if (code <= 2) return "⛅";
        if (code == 3) return "☁";
        if (code <= 48) return "🌫";
        if (code <= 67 || (code >= 80 && code <= 82)) return "🌧";
        if (code <= 77 || code == 85 || code == 86) return "❄";
        return "⛈";
    }
}
