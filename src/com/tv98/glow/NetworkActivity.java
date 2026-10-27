package com.tv98.glow;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;

/**
 * Glow ▸ Settings ▸ Network: how the box is connected (Wi-Fi signal and link speed, or cable), a speed test against
 * Cloudflare's public test server, and the data used today and this month.
 */
public class NetworkActivity extends Activity {
    private static final String TEST = "https://speed.cloudflare.com/__down?bytes=";
    private LinearLayout col;
    private TextView speed;
    private boolean testing;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new Ui.Aurora());
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(140), Ui.dp(36), Ui.dp(140), Ui.dp(36));
        scroll.addView(col);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        DataUsage.sample(this);
        fill();
    }

    private void fill() {
        col.removeAllViews();
        TextView h = Ui.text(this, 26, Ui.TEXT, 300);
        h.setText("Network");
        h.setPadding(0, 0, 0, Ui.dp(14));
        col.addView(h);

        String[] link = connection();
        row(link[0], link[1], null);

        speed = row("Speed test", "Press OK to measure the box's download speed (about 10 seconds, up to 50 MB).", v -> runTest()).findViewWithTag("sub");

        row("Data used: " + DataUsage.size(DataUsage.today(this)) + " today, " + DataUsage.size(DataUsage.thisMonth(this)) + " this month",
                L.f("Since the box started: {} (Glow itself {}). Counted by Glow from when it was installed; per app would need Android's usage access for Glow.",
                        DataUsage.size(DataUsage.sinceStart()), DataUsage.size(DataUsage.glow())), null);

        row("Wi-Fi settings", "Change network, forget a network, or set a fixed address (Android settings).", v -> {
            try {
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
            } catch (Exception ignored) {
            }
        });
        if (col.getChildCount() > 2) col.getChildAt(2).requestFocus();
    }

    /** {title, detail}: "Wi-Fi · strong signal (−48 dBm)", "5 GHz · link 433 Mbit/s · 192.168.1.124". */
    private String[] connection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        String ip = "";
        if (lp != null) {
            for (LinkAddress a : lp.getLinkAddresses()) {
                if (a.getAddress() instanceof java.net.Inet4Address) ip = a.getAddress().getHostAddress();
            }
        }
        if (caps == null) return new String[] {L.t("Not connected"), L.t("The box has no network right now. Check the Wi-Fi or the cable.")};
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return new String[] {L.t("Cable (Ethernet)"), ip};
        }
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo w = wm.getConnectionInfo();
        int rssi = w.getRssi();
        // This box's Wi-Fi driver sometimes reports impossible values (+9, −22 dBm): then say so rather than guess.
        boolean real = rssi <= -30 && rssi >= -100;
        String strength = !real ? "signal unknown" : rssi >= -55 ? "strong signal" : rssi >= -67 ? "good signal" : rssi >= -75 ? "fair signal" : "weak signal";
        String band = w.getFrequency() >= 4900 ? "5 GHz" : w.getFrequency() > 0 ? "2.4 GHz" : "";
        String detail = (band.isEmpty() ? "" : band + "   ·   ") + L.f("link {n} Mbit/s", w.getLinkSpeed()) + (ip.isEmpty() ? "" : "   ·   " + ip)
                + (real && rssi < -70 ? "\n" + L.t("A weak signal can make channels stutter: a cable, or the box nearer the router, helps.") : "");
        return new String[] {L.t("Wi-Fi") + "   ·   " + L.t(strength) + (real ? " (" + rssi + " dBm)" : ""), detail};
    }

    /** Downloads from Cloudflare's speed test for up to 10 s (two connections) and a few tiny requests for the delay. */
    private void runTest() {
        if (testing) return;
        testing = true;
        speed.setText(L.t("Measuring…"));
        Net.IO.execute(() -> {
            long[] ping = new long[5];
            for (int i = 0; i < ping.length; i++) {
                long t0 = SystemClock.elapsedRealtime();
                try {
                    read(TEST + "0", 1 << 10, 5_000);
                    ping[i] = SystemClock.elapsedRealtime() - t0;
                } catch (Exception e) {
                    ping[i] = 9_999;
                }
            }
            Arrays.sort(ping);
            final long[] bytes = new long[2];
            final long start = SystemClock.elapsedRealtime();
            Thread[] t = new Thread[2];
            for (int k = 0; k < t.length; k++) {
                final int slot = k;
                t[k] = new Thread(() -> {
                    try {
                        bytes[slot] = read(TEST + 25_000_000, 25_000_000, 10_000 - (SystemClock.elapsedRealtime() - start));
                    } catch (Exception ignored) {
                    }
                });
                t[k].start();
            }
            for (Thread x : t) {
                try {
                    x.join(12_000);
                } catch (InterruptedException ignored) {
                }
            }
            double secs = Math.max(0.5, (SystemClock.elapsedRealtime() - start) / 1000.0);
            double mbps = (bytes[0] + bytes[1]) * 8 / secs / 1e6;
            long delay = ping[ping.length / 2];
            Net.MAIN.post(() -> {
                testing = false;
                if (isFinishing()) return;
                String good = mbps >= 25 ? "enough for 4K" : mbps >= 8 ? "enough for HD and full HD" : mbps >= 3 ? "enough for standard quality" : "too slow for smooth TV";
                speed.setText(mbps < 0.1 ? L.t("The test server didn't answer. Check the internet connection.")
                        : String.format(Locale.ROOT, "%.0f Mbit/s", mbps) + "   ·   " + L.f("delay {n} ms", delay) + "   ·   " + L.t(good)
                        + "\n" + L.t("Press OK to measure again."));
                DataUsage.sample(this);
            });
        });
    }

    /** Reads up to {@code max} bytes (or until {@code ms} runs out), returns how many arrived. */
    private static long read(String url, long max, long ms) throws Exception {
        long until = SystemClock.elapsedRealtime() + Math.max(1_000, ms);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5_000);
        c.setReadTimeout(5_000);
        c.setRequestProperty("User-Agent", "Glow/1.0 (Android TV)");
        long n = 0;
        boolean whole = false;
        try (InputStream in = c.getInputStream()) {
            byte[] b = new byte[64 * 1024];
            int r;
            while (n < max && SystemClock.elapsedRealtime() < until && (r = in.read(b)) > 0) n += r;
            whole = n >= max || in.read() < 0;
        } finally {
            // A small reply read to the end leaves the connection open for the next one (so the delay measured is
            // the network's, not a new secure connection's); a cut-off download is closed.
            if (!whole) c.disconnect();
        }
        return n;
    }

    private View row(String title, String sub, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setFocusable(true);
        r.setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(20), Ui.dp(14));
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.glass(Ui.dp(16), true));
        bg.addState(new int[] {}, Ui.glass(Ui.dp(16), false));
        r.setBackground(bg);
        if (click != null) {
            r.setClickable(true);
            r.setOnClickListener(click);
        }
        TextView t = Ui.text(this, 17, Ui.TEXT, 600);
        t.setText(title);
        TextView s = Ui.text(this, 12.5f, Ui.TEXT_DIM, 400);
        s.setText(sub);
        s.setTag("sub");
        s.setLineSpacing(0, 1.2f);
        s.setPadding(0, Ui.dp(5), 0, 0);
        r.addView(t);
        r.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(10);
        col.addView(r, lp);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }
}
