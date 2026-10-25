package com.tv98.glow;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Intelligence: a live picture of the world from free public sources. World map (earthquakes, disaster alerts,
 * natural events, news hotspots, webcams, the ISS, day and night), live city cams, weather, and what prediction
 * markets expect.
 */
public class IntelActivity extends Activity {
    static final String EXTRA_SECTION = "section";
    static final int MAP = 0, CAMS = 1, WEATHER = 2, PREDICTIONS = 3;
    private static final String[] SECTIONS = {"World map", "Live cams", "Weather", "Predictions"};
    private static final String[][] FILTERS = {{"All", ""}, {"Earthquakes", Intel.QUAKE}, {"Disaster alerts", Intel.ALERT},
            {"Natural events", Intel.NATURAL}, {"News hotspots", Intel.NEWS}, {"Live cams", Intel.CAM}};

    private ListView rail;
    private FrameLayout content;
    private View mapPane, camsPane, weatherPane, marketsPane;
    private WorldMapView map;
    private TextView mapHeading, evTitle, evDetail, evMeta, evHint;
    private LinearLayout chips;
    private final List<Intel.Event> all = new ArrayList<>();
    private List<Intel.Event> shown = new ArrayList<>();
    private int filter, pos;
    private int section = -1;
    private final Runnable issTick = new Runnable() {
        @Override
        public void run() {
            Net.IO.execute(() -> {
                double[] ll = Intel.iss();
                Net.MAIN.post(() -> { if (ll != null && map != null) map.setIss(ll); });
            });
            content.postDelayed(this, 10_000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        LinearLayout root = new LinearLayout(this);
        root.setBackground(new Ui.Aurora());
        root.setPadding(Ui.dp(24), Ui.dp(22), Ui.dp(24), Ui.dp(18));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setBackground(Ui.glass(Ui.dp(18), false));
        left.setPadding(Ui.dp(10), Ui.dp(16), Ui.dp(10), Ui.dp(10));
        root.addView(left, new LinearLayout.LayoutParams(Ui.dp(150), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView t = Ui.label(this, "Intelligence");
        t.setPadding(Ui.dp(12), 0, 0, Ui.dp(10));
        left.addView(t);
        rail = new ListView(this) {
            @Override
            protected void onFocusChanged(boolean gain, int dir, android.graphics.Rect prev) {
                // Coming back from a section, stay on it (a ListView otherwise picks the row nearest the panel's middle).
                super.onFocusChanged(gain, dir, null);
                if (gain && section >= 0) setSelection(section);
            }
        };
        rail.setDivider(null);
        rail.setSelector(Ui.glass(Ui.dp(10), true));
        rail.setVerticalScrollBarEnabled(false);
        rail.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return SECTIONS.length; }
            @Override public Object getItem(int i) { return SECTIONS[i]; }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View convert, ViewGroup parent) {
                TextView tv = convert instanceof TextView ? (TextView) convert : Ui.text(IntelActivity.this, 15, Ui.TEXT, 400);
                tv.setText(SECTIONS[i]);
                tv.setPadding(Ui.dp(14), Ui.dp(11), Ui.dp(10), Ui.dp(11));
                return tv;
            }
        });
        rail.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int i, long id) { show(i); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        rail.setOnItemClickListener((p, v, i, id) -> focusContent());
        left.addView(rail, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView credit = Ui.text(this, 9.5f, Ui.TEXT_DIM, 400);
        credit.setText("USGS · GDACS · NASA EONET · World Monitor · Open-Meteo · Polymarket · Natural Earth · wheretheiss.at");
        credit.setLineSpacing(0, 1.15f);
        credit.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(6), Ui.dp(2));
        left.addView(credit);

        content = new FrameLayout(this);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        clp.leftMargin = Ui.dp(18);
        root.addView(content, clp);
        mapPane = buildMap();
        camsPane = buildCams();
        weatherPane = new LinearLayout(this);
        marketsPane = new LinearLayout(this);
        for (View v : new View[] {mapPane, camsPane, weatherPane, marketsPane}) {
            v.setVisibility(View.GONE);
            content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        setContentView(root);

        int start = getIntent().getIntExtra(EXTRA_SECTION, MAP);
        rail.setSelection(start);
        show(start);
        if (getIntent().hasExtra(EXTRA_SECTION)) content.post(this::focusContent);
        else rail.requestFocus();
        loadMap();
    }

    private void show(int i) {
        if (i == section) return;
        section = i;
        mapPane.setVisibility(i == MAP ? View.VISIBLE : View.GONE);
        camsPane.setVisibility(i == CAMS ? View.VISIBLE : View.GONE);
        weatherPane.setVisibility(i == WEATHER ? View.VISIBLE : View.GONE);
        marketsPane.setVisibility(i == PREDICTIONS ? View.VISIBLE : View.GONE);
        if (i == WEATHER) loadWeather();
        if (i == PREDICTIONS) loadMarkets();
    }

    private void focusContent() {
        View target = section == MAP ? mapPane : section == CAMS ? camsPane : section == PREDICTIONS ? marketsPane : null;
        if (target == null || !target.requestFocus()) rail.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        issTick.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        content.removeCallbacks(issTick);
    }

    // ---- world map ----

    private View buildMap() {
        LinearLayout pane = new LinearLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && hasFocus() && onMapKey(e.getKeyCode())) return true;
                return super.dispatchKeyEvent(e);
            }
        };
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setFocusable(true);
        mapHeading = Ui.text(this, 21, Ui.TEXT, 300);
        mapHeading.setSingleLine();
        mapHeading.setText("World now");
        pane.addView(mapHeading);
        map = new WorldMapView(this);
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(Ui.dp(14));
        map.setClipToOutline(true);
        map.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(14)); }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        mlp.topMargin = Ui.dp(10);
        pane.addView(map, mlp);

        chips = new LinearLayout(this);
        chips.setPadding(0, Ui.dp(10), 0, 0);
        pane.addView(chips);

        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(Ui.dp(4), Ui.dp(10), 0, 0);
        evTitle = Ui.text(this, 17, Ui.TEXT, 600);
        evTitle.setSingleLine();
        evTitle.setEllipsize(TextUtils.TruncateAt.END);
        evDetail = Ui.text(this, 13, 0xCCF2F4FF, 400);
        evDetail.setSingleLine();
        evDetail.setEllipsize(TextUtils.TruncateAt.END);
        evDetail.setPadding(0, Ui.dp(4), 0, 0);
        evMeta = Ui.text(this, 11.5f, Ui.TEXT_DIM, 400);
        evMeta.setPadding(0, Ui.dp(4), 0, 0);
        evHint = Ui.text(this, 11.5f, Ui.ACCENT, 600);
        evHint.setPadding(0, Ui.dp(6), 0, 0);
        detail.addView(evTitle);
        detail.addView(evDetail);
        detail.addView(evMeta);
        detail.addView(evHint);
        pane.addView(detail);
        drawChips();
        return pane;
    }

    private void drawChips() {
        chips.removeAllViews();
        for (int i = 0; i < FILTERS.length; i++) {
            int n = count(FILTERS[i][1]);
            TextView c = Ui.text(this, 12, i == filter ? 0xFF0B0E1C : Ui.TEXT, 600);
            c.setText(FILTERS[i][0] + (all.isEmpty() ? "" : "  " + n));
            c.setPadding(Ui.dp(12), Ui.dp(5), Ui.dp(12), Ui.dp(5));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Ui.dp(12));
            g.setColor(i == filter ? Ui.ACCENT : 0x22FFFFFF);
            c.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(8);
            chips.addView(c, lp);
        }
    }

    private int count(String kind) {
        if (kind.isEmpty()) return all.size();
        int n = 0;
        for (Intel.Event e : all) if (e.kind.equals(kind)) n++;
        return n;
    }

    // The last world picture, reused for five minutes so reopening is instant and doesn't re-read every feed.
    private static List<Intel.Event> recent;
    private static long recentAt;

    private void loadMap() {
        final long t0 = android.os.SystemClock.uptimeMillis();
        Net.IO.execute(() -> {
            List<Intel.Event> list;
            synchronized (IntelActivity.class) {
                list = recent != null && System.currentTimeMillis() - recentAt < 5 * 60_000L ? recent : null;
            }
            if (list == null) list = collect();
            final List<Intel.Event> ready = list;
            Net.MAIN.post(() -> {
                if (isFinishing()) return;
                all.clear();
                all.addAll(ready);
                applyFilter();
                android.util.Log.i("GlowPerf", "intel map ready in " + (android.os.SystemClock.uptimeMillis() - t0) + " ms, " + ready.size() + " events");
                String fmt = Ui.is24h(this) ? "HH:mm" : "h:mm a";
                mapHeading.setText("World now   ·   updated " + DateFormat.format(fmt, recentAt)
                        + "   ·   night side shaded   ·   ISS live");
            });
        });
    }

    private List<Intel.Event> collect() {
        if (World.stale(this)) World.refresh(this);
        List<Intel.Event> list = new ArrayList<>();
        list.addAll(Intel.alerts(this));
        list.addAll(Intel.quakes(this));
        list.addAll(Intel.newsHotspots(this));
        list.addAll(Intel.natural(this));
        list.addAll(Intel.cams());
        // Most serious first, then newest.
        Collections.sort(list, (x, y) -> x.severity != y.severity ? y.severity - x.severity : Long.compare(y.at, x.at));
        synchronized (IntelActivity.class) {
            recent = list;
            recentAt = System.currentTimeMillis();
        }
        return list;
    }

    private void applyFilter() {
        String kind = FILTERS[filter][1];
        shown = new ArrayList<>();
        for (Intel.Event e : all) if (kind.isEmpty() || e.kind.equals(kind)) shown.add(e);
        map.setEvents(kind.isEmpty() ? all : shown);
        pos = 0;
        drawChips();
        select();
    }

    private void select() {
        if (shown.isEmpty()) {
            map.setSelected(null);
            evTitle.setText(all.isEmpty() ? "Loading the world…" : "Nothing in this view right now");
            evDetail.setText("");
            evMeta.setText("");
            evHint.setText("▲ ▼ change what's shown");
            return;
        }
        Intel.Event e = shown.get(pos);
        map.setSelected(e);
        evTitle.setText(e.title);
        evTitle.setTextColor(WorldMapView.colour(e));
        evDetail.setText(e.detail);
        evMeta.setText(e.source + (e.at > 0 ? "   ·   " + World.ago(e.at) : "") + "   ·   " + (pos + 1) + " of " + shown.size());
        String ok = e.kind.equals(Intel.CAM) ? "OK  watch this live cam" : "OK  watch live news about this";
        evHint.setText("◀ ▶ next   ·   ▲ ▼ " + FILTERS[(filter + 1) % FILTERS.length][0].toLowerCase(Locale.ROOT) + "…   ·   " + ok);
    }

    private boolean onMapKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!shown.isEmpty()) pos = (pos + 1) % shown.size();
                select();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (pos == 0) return false; // back to the sections
                pos--;
                select();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                filter = (filter + 1) % FILTERS.length;
                applyFilter();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                filter = (filter + FILTERS.length - 1) % FILTERS.length;
                applyFilter();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!shown.isEmpty()) act(shown.get(pos));
                return true;
            default:
                return false;
        }
    }

    /** OK on a pin: a cam opens; anything else tunes to a news channel covering that part of the world. */
    private void act(Intel.Event e) {
        if (e.kind.equals(Intel.CAM)) {
            openCam(this, e.count);
            return;
        }
        final World.Story s = new World.Story();
        s.title = e.title + " " + e.detail;
        s.source = "";
        s.category = region(e.lat, e.lon);
        Net.IO.execute(() -> {
            Channel ch = World.liveFor(this, s);
            Net.MAIN.post(() -> {
                if (ch == null || isFinishing()) return;
                List<Channel> list = new ArrayList<>();
                GuideLineup l = GuideLineup.current();
                if (l != null) for (GuideLineup.Section sec : l.main) if (sec.name.equals("News")) list.addAll(sec.channels);
                if (!list.contains(ch)) list.add(0, ch);
                Xtream.playlist = list;
                startActivity(new Intent(this, PlayerActivity.class).putExtra("index", list.indexOf(ch)));
            });
        });
    }

    /** World Monitor's region names, roughly from coordinates (for picking a news channel). */
    static String region(double lat, double lon) {
        if (lon >= 25 && lon <= 63 && lat >= 12 && lat <= 42) return "middleeast";
        if (lon >= -25 && lon <= 50 && lat >= -35 && lat < 35) return "africa";
        if (lon >= -25 && lon <= 45 && lat >= 35) return "europe";
        if (lon >= 60) return "asia";
        if (lon <= -30 && lat < 15) return "latam";
        if (lon <= -30) return "us";
        return "*";
    }

    // ---- live cams ----

    private View buildCams() {
        LinearLayout pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);
        TextView h = Ui.text(this, 21, Ui.TEXT, 300);
        h.setText("Live cams   ·   " + WmCams.CAMS.length + " places, live now");
        pane.addView(h);
        // All 22 cards are built once (no recycling), so each keeps its cached GPU layer while the grid scrolls:
        // a recycling GridView redrew layers on every reuse (16-26 % late frames; 95 % without layers).
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setSmoothScrollingEnabled(true); // clipped to its box (the rows' padding leaves room for the glow)
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setPadding(Ui.dp(Tile.GLOW_DP + 2), Ui.dp(16), Ui.dp(Tile.GLOW_DP + 2), Ui.dp(16));
        LinearLayout row = null;
        for (int i = 0; i < WmCams.CAMS.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(128));
                if (i > 0) rlp.topMargin = Ui.dp(14);
                rows.addView(row, rlp);
            }
            final int idx = i;
            Tile t = camTile(this, 1, 128);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            tlp.setMargins(i % 3 == 0 ? 0 : Ui.dp(7), 0, i % 3 == 2 ? 0 : Ui.dp(7), 0);
            t.setLayoutParams(tlp);
            fillCam(this, t, i);
            t.setOnClickListener(v -> openCam(this, idx));
            row.addView(t);
        }
        while (row != null && row.getChildCount() < 3) { // keep the last row's cards the same size
            View gap = new View(this);
            row.addView(gap, new LinearLayout.LayoutParams(0, 1, 1));
        }
        scroll.addView(rows);
        pane.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return pane;
    }

    /** A cam card: the stream's live thumbnail, place and what it shows. */
    static Tile camTile(Context c, int wDp, int hDp) {
        Tile t = new Tile(c, wDp, hDp);
        t.setFocusable(wDp > 0);
        t.setClickable(wDp > 0);
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        t.card.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(Ui.dp(10), Ui.dp(18), Ui.dp(10), Ui.dp(8));
        words.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] {0xE60B0E1C, 0x000B0E1C}));
        TextView name = Ui.text(c, 13.5f, Ui.TEXT, 600);
        name.setSingleLine();
        TextView sub = Ui.text(c, 10.5f, Ui.TEXT_DIM, 400);
        sub.setSingleLine();
        words.addView(name);
        words.addView(sub);
        t.card.addView(words, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        TextView live = Ui.text(c, 9.5f, 0xFFFFFFFF, 600);
        live.setText(" ● LIVE ");
        GradientDrawable lg = new GradientDrawable();
        lg.setCornerRadius(Ui.dp(4));
        lg.setColor(0xDDE0312B);
        live.setBackground(lg);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        llp.setMargins(Ui.dp(8), Ui.dp(8), 0, 0);
        t.card.addView(live, llp);
        return t;
    }

    static void fillCam(Context c, Tile t, int i) {
        String[] r = WmCams.CAMS[i];
        ImageView img = (ImageView) t.card.getChildAt(0);
        LinearLayout words = (LinearLayout) t.card.getChildAt(1);
        ((TextView) words.getChildAt(0)).setText(r[1]);
        ((TextView) words.getChildAt(1)).setText(r[2] + "  ·  " + r[3]);
        img.setBackground(new SourceArt(r[1], 0xFF2C5FB8, Ui.dp(14)));
        // A recent frame of the stream from YouTube: 320x180 (16:9, ~225 KB decoded) rather than hqdefault's
        // letterboxed 480x360 (~690 KB) – it fills the card without bars at a third of the memory.
        Net.image(img, "https://i.ytimg.com/vi/" + r[6] + "/mqdefault_live.jpg", Ui.dp(240), null);
        t.setContentDescription(r[1] + " live cam");
    }

    /** Opens a cam in the YouTube app (they're YouTube live streams). */
    static void openCam(Context c, int i) {
        String id = WmCams.CAMS[i][6];
        Intent yt = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + id))
                .setPackage("com.google.android.youtube.tv").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(yt);
        } catch (Exception e) {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    // ---- weather ----

    private void loadWeather() {
        Net.IO.execute(() -> {
            Intel.Weather w = Intel.weather(this);
            Net.MAIN.post(() -> { if (!isFinishing()) fillWeather(w); });
        });
    }

    private void fillWeather(Intel.Weather w) {
        LinearLayout pane = (LinearLayout) weatherPane;
        pane.removeAllViews();
        pane.setOrientation(LinearLayout.VERTICAL);
        if (w.code < 0) {
            TextView none = Ui.text(this, 18, Ui.TEXT_DIM, 400);
            none.setText("Weather isn't available right now. Check the internet connection.");
            pane.addView(none);
            return;
        }
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = Ui.text(this, 64, Ui.TEXT, 300);
        icon.setText(com.tv98.glow.Weather.symbol(w.code));
        top.addView(icon);
        LinearLayout now = new LinearLayout(this);
        now.setOrientation(LinearLayout.VERTICAL);
        now.setPadding(Ui.dp(18), 0, 0, 0);
        TextView temp = Ui.text(this, 54, Ui.TEXT, 300);
        temp.setText(Math.round(w.temp) + "°");
        TextView city = Ui.text(this, 16, Ui.TEXT, 600);
        city.setText(w.city + "   ·   feels like " + Math.round(w.feels) + "°   ·   H " + Math.round(w.max) + "°  L " + Math.round(w.min) + "°");
        TextView sun = Ui.text(this, 13, Ui.TEXT_DIM, 400);
        String fmt = Ui.is24h(this) ? "HH:mm" : "h:mm a";
        sun.setText("Sunrise " + DateFormat.format(fmt, w.sunrise) + "   ·   Sunset " + DateFormat.format(fmt, w.sunset));
        sun.setPadding(0, Ui.dp(6), 0, 0);
        now.addView(temp);
        now.addView(city);
        now.addView(sun);
        top.addView(now, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (w.aqi >= 0) {
            Object[] band = Intel.aqiBand(w.aqi);
            LinearLayout aq = new LinearLayout(this);
            aq.setOrientation(LinearLayout.VERTICAL);
            aq.setGravity(Gravity.CENTER);
            aq.setPadding(Ui.dp(18), Ui.dp(10), Ui.dp(18), Ui.dp(10));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Ui.dp(14));
            g.setStroke(Ui.dp(2), (int) band[1]);
            g.setColor(0x220B0E1C);
            aq.setBackground(g);
            TextView a1 = Ui.text(this, 30, (int) band[1], 600);
            a1.setText(String.valueOf(w.aqi));
            TextView a2 = Ui.text(this, 11, Ui.TEXT, 600);
            a2.setText("AIR QUALITY  ·  " + band[0]);
            TextView a3 = Ui.text(this, 11, Ui.TEXT_DIM, 400);
            a3.setText(w.pm25 >= 0 ? String.format(Locale.ROOT, "PM2.5 %.0f µg/m³", w.pm25) : "");
            aq.addView(a1);
            aq.addView(a2);
            aq.addView(a3);
            top.addView(aq);
        }
        pane.addView(top);

        pane.addView(sectionLabel("Next 24 hours"));
        LinearLayout hours = new LinearLayout(this);
        for (int i = 0; i < w.hourly.size() && i < 24; i += 2) {
            long[] h = w.hourly.get(i);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView tt = Ui.text(this, 11, Ui.TEXT_DIM, 400);
            tt.setText(DateFormat.format(Ui.is24h(this) ? "HH:mm" : "ha", h[0]).toString().toLowerCase(Locale.ROOT));
            TextView ic = Ui.text(this, 20, Ui.TEXT, 400);
            ic.setText(com.tv98.glow.Weather.symbol((int) h[2]));
            ic.setPadding(0, Ui.dp(4), 0, Ui.dp(4));
            TextView tv = Ui.text(this, 14, Ui.TEXT, 600);
            tv.setText(Math.round(h[1] / 10.0) + "°");
            TextView rp = Ui.text(this, 10.5f, 0xFF8FC8FF, 400);
            rp.setText(h[3] > 0 ? h[3] + "%" : "");
            for (TextView x : new TextView[] {tt, ic, tv, rp}) {
                x.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.addView(x, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            hours.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        hours.setBackground(Ui.glass(Ui.dp(14), false));
        hours.setPadding(0, Ui.dp(10), 0, Ui.dp(10));
        pane.addView(hours);

        pane.addView(sectionLabel("7 days"));
        LinearLayout days = new LinearLayout(this);
        for (int i = 0; i < w.daily.size(); i++) {
            long[] d = w.daily.get(i);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView dn = Ui.text(this, 12, Ui.TEXT, 600);
            dn.setText(i == 0 ? "Today" : DateFormat.format("EEE", d[0]).toString());
            TextView ic = Ui.text(this, 24, Ui.TEXT, 400);
            ic.setText(com.tv98.glow.Weather.symbol((int) d[3]));
            ic.setPadding(0, Ui.dp(4), 0, Ui.dp(4));
            TextView mm = Ui.text(this, 13, Ui.TEXT, 600);
            mm.setText(Math.round(d[1] / 10.0) + "°  " + Math.round(d[2] / 10.0) + "°");
            TextView rp = Ui.text(this, 10.5f, 0xFF8FC8FF, 400);
            rp.setText(d[4] > 0 ? "rain " + d[4] + "%" : "");
            for (TextView x : new TextView[] {dn, ic, mm, rp}) {
                x.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.addView(x, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            days.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        days.setBackground(Ui.glass(Ui.dp(14), false));
        days.setPadding(0, Ui.dp(10), 0, Ui.dp(10));
        pane.addView(days);
    }

    private TextView sectionLabel(String s) {
        TextView t = Ui.text(this, 12, Ui.TEXT_DIM, 600);
        t.setText(s.toUpperCase(Locale.ROOT));
        t.setLetterSpacing(0.12f);
        t.setPadding(Ui.dp(4), Ui.dp(14), 0, Ui.dp(6));
        return t;
    }

    // ---- prediction markets ----

    private void loadMarkets() {
        Net.IO.execute(() -> {
            List<Intel.Market> m = Intel.markets(this);
            Net.MAIN.post(() -> { if (!isFinishing()) fillMarkets(m); });
        });
    }

    private void fillMarkets(List<Intel.Market> markets) {
        LinearLayout pane = (LinearLayout) marketsPane;
        pane.removeAllViews();
        pane.setOrientation(LinearLayout.VERTICAL);
        TextView h = Ui.text(this, 21, Ui.TEXT, 300);
        h.setText("What the world expects");
        pane.addView(h);
        TextView note = Ui.text(this, 11.5f, Ui.TEXT_DIM, 400);
        note.setText("Prediction-market odds from Polymarket's most-traded open questions today, for information only.");
        note.setPadding(0, Ui.dp(4), 0, Ui.dp(10));
        pane.addView(note);
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(Ui.dp(6));
        list.setSelector(Ui.glass(Ui.dp(12), true));
        list.setVerticalScrollBarEnabled(false);
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return markets.size(); }
            @Override public Object getItem(int i) { return markets.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View convert, ViewGroup parent) {
                Intel.Market m = markets.get(i);
                LinearLayout row = new LinearLayout(IntelActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
                row.setBackground(Ui.glass(Ui.dp(12), false));
                LinearLayout words = new LinearLayout(IntelActivity.this);
                words.setOrientation(LinearLayout.VERTICAL);
                TextView q = Ui.text(IntelActivity.this, 14, Ui.TEXT, 600);
                q.setText(m.question);
                q.setMaxLines(2);
                q.setEllipsize(TextUtils.TruncateAt.END);
                TextView meta = Ui.text(IntelActivity.this, 11, Ui.TEXT_DIM, 400);
                meta.setText(money(m.volume24h) + " traded today" + (m.endDate.length() >= 10 ? "   ·   ends " + m.endDate.substring(0, 10) : ""));
                meta.setPadding(0, Ui.dp(4), 0, 0);
                words.addView(q);
                words.addView(meta);
                row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                int pct = (int) Math.round(m.yes * 100);
                TextView p = Ui.text(IntelActivity.this, 24, pct >= 50 ? 0xFF6BE3A0 : 0xFFFFB86B, 600);
                p.setText(pct + "%");
                p.setGravity(Gravity.END);
                TextView yes = Ui.text(IntelActivity.this, 10, Ui.TEXT_DIM, 600);
                yes.setText("CHANCE OF YES");
                LinearLayout pr = new LinearLayout(IntelActivity.this);
                pr.setOrientation(LinearLayout.VERTICAL);
                pr.setGravity(Gravity.END);
                pr.addView(p);
                pr.addView(yes);
                row.addView(pr, new LinearLayout.LayoutParams(Ui.dp(110), ViewGroup.LayoutParams.WRAP_CONTENT));
                return row;
            }
        });
        pane.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private static String money(double v) {
        if (v >= 1e6) return String.format(Locale.ROOT, "$%.1fM", v / 1e6);
        if (v >= 1e3) return String.format(Locale.ROOT, "$%.0fK", v / 1e3);
        return String.format(Locale.ROOT, "$%.0f", v);
    }
}
