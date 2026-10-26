package com.tv98.glow;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Markets: Bitcoin, Ether and gold from CoinGecko (no key; gold is PAX Gold, a token backed by one troy ounce, so
 * it follows the gold price) with seven days of prices, and exchange rates from the European Central Bank via
 * Frankfurter (no key; working days, set around 16:00 CET) with two weeks of history. For information only.
 */
final class Markets {
    static final class Quote {
        String name = "", symbol = "";
        double price, change; // change: % over 24 h for coins, since the previous ECB day for currencies
        boolean currency;
        double[] history = new double[0];
    }

    private static final String COINS = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd"
            + "&ids=bitcoin,ethereum,pax-gold&sparkline=true&price_change_percentage=24h";
    private static final String[][] COIN_NAMES = {{"bitcoin", "Bitcoin", "BTC"}, {"ethereum", "Ether", "ETH"}, {"pax-gold", "Gold, 1 oz", "XAU"}};
    /** {from, to, label}: what a Thai or British household checks. */
    private static final String[][] PAIRS = {{"USD", "THB", "US dollar in baht"}, {"GBP", "THB", "Pound in baht"},
            {"EUR", "THB", "Euro in baht"}, {"GBP", "USD", "Pound in dollars"}};

    private static List<Quote> memo;
    private static long memoAt;

    private Markets() {}

    /** Off the main thread. Coins refresh every 10 minutes, rates every 6 hours; reused for a minute. */
    static synchronized List<Quote> load(Context c) {
        if (memo != null && System.currentTimeMillis() - memoAt < 60_000L) return memo;
        List<Quote> out = new ArrayList<>();
        try {
            String s = Intel.cached(c, "coins", COINS, 10 * 60_000L);
            JSONArray a = s == null ? new JSONArray() : new JSONArray(s);
            for (String[] want : COIN_NAMES) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject x = a.getJSONObject(i);
                    if (!x.optString("id").equals(want[0])) continue;
                    Quote q = new Quote();
                    q.name = want[1];
                    q.symbol = want[2];
                    q.price = x.optDouble("current_price");
                    q.change = x.optDouble("price_change_percentage_24h", 0);
                    JSONObject sp = x.optJSONObject("sparkline_in_7d");
                    JSONArray p = sp == null ? null : sp.optJSONArray("price");
                    if (p != null) {
                        q.history = new double[p.length()];
                        for (int k = 0; k < p.length(); k++) q.history[k] = p.optDouble(k);
                    }
                    out.add(q);
                }
            }
        } catch (Exception e) {
            android.util.Log.i("GlowPerf", "coins: " + e);
        }
        for (String[] pair : PAIRS) {
            try {
                Quote q = rate(c, pair[0], pair[1]);
                if (q == null) continue;
                q.name = pair[2];
                out.add(q);
            } catch (Exception e) {
                android.util.Log.i("GlowPerf", "rates: " + e);
            }
        }
        memo = out;
        memoAt = System.currentTimeMillis();
        return out;
    }

    private static Quote rate(Context c, String from, String to) throws Exception {
        java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        String since = day.format(new java.util.Date(System.currentTimeMillis() - 21 * 86_400_000L));
        String s = Intel.cached(c, "fx-" + from + to, "https://api.frankfurter.dev/v1/" + since + "..?from=" + from + "&to=" + to,
                6 * 3_600_000L);
        if (s == null) return null;
        JSONObject rates = new JSONObject(s).optJSONObject("rates");
        if (rates == null) return null;
        TreeMap<String, Double> byDay = new TreeMap<>(); // dates sort as text
        for (Iterator<String> it = rates.keys(); it.hasNext(); ) {
            String d = it.next();
            byDay.put(d, rates.getJSONObject(d).optDouble(to));
        }
        if (byDay.isEmpty()) return null;
        Quote q = new Quote();
        q.currency = true;
        q.symbol = from + "/" + to;
        q.history = new double[byDay.size()];
        int i = 0;
        for (double v : byDay.values()) q.history[i++] = v;
        q.price = q.history[q.history.length - 1];
        q.change = q.history.length > 1 ? (q.price / q.history[q.history.length - 2] - 1) * 100 : 0;
        return q;
    }

    /** "$79,939", "฿33.75", "1.3368". */
    static String price(Quote q) {
        if (q.currency) {
            String sign = q.symbol.endsWith("THB") ? "฿" : q.symbol.endsWith("USD") ? "$" : "";
            return sign + String.format(Locale.ROOT, q.price >= 10 ? "%.2f" : "%.4f", q.price);
        }
        return "$" + String.format(Locale.US, q.price >= 1000 ? "%,.0f" : "%,.2f", q.price);
    }

    static String change(Quote q) {
        return String.format(Locale.ROOT, "%+.1f%%", q.change).replace('-', '−');
    }

    /** One line for Home's card: "BTC $79,939 −0.7%   ·   Gold $4,013 +0.4%". */
    static String line(List<Quote> qs) {
        StringBuilder b = new StringBuilder();
        for (Quote q : qs) {
            if (q.currency || b.length() > 60) continue;
            if (b.length() > 0) b.append("   ·   ");
            b.append(q.symbol.equals("XAU") ? "Gold" : q.symbol).append(' ').append(price(q)).append(' ').append(change(q));
        }
        return b.toString();
    }

    /** The dollar in baht for Home's card, or "". */
    static String baht(List<Quote> qs) {
        for (Quote q : qs) if (q.symbol.equals("USD/THB")) return "$1 = " + price(q);
        return "";
    }
}
