package com.tv98.glow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The classic blue programme grid, drawn as a single view (one draw pass, no per-cell views): channel
 * column on the left, three half-hour columns, dark-blue programme blocks, yellow highlight.
 *   ▲▼ channel · ◀▶ previous/next programme · CH± page · digits jump to a channel number · OK watch.
 *
 * Rows other than the highlighted one are shown from pictures, drawn on a background thread: the rows on screen
 * first, then (while the remote is idle) the rows just above and below and the next and previous time windows, so
 * a key press finds its rows already drawn. Until a row's picture arrives it's drawn directly.
 */
class GuideGridView extends View {
    interface Listener {
        void onSelect(Channel ch, Guide.Prog prog, int number);
        void onWatch(Channel ch, int indexInList);
        /** OK on a programme that hasn't started: set or clear a reminder. */
        void onRemind(Channel ch, Guide.Prog prog);
        void onNeedGuide(List<Channel> visible);
    }

    static final int SKY_TOP = 0xFF6FA8E8, SKY_BOTTOM = 0xFF2C5FB8;
    static final int CELL = 0xFF1C3A8E, CELL_EDGE = 0xFF4C78D0, CELL_TEXT = 0xFFFFFFFF;
    static final int HILITE = 0xFFF5D33C, HILITE_TEXT = 0xFF14306F, DIM_TEXT = 0xFFB9CCF2;
    /** Behind each row's cells (the lines between programmes): rows are opaque, so the GPU copies them without blending. */
    static final int LINES = 0xFF13296B;
    private static final long SLOT = 30 * 60 * 1000L, WINDOW = 3 * SLOT, GAP = 5 * 60 * 1000L;

    private final android.text.TextPaint header = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marker = new Paint();
    private final float rowH, gap, chanW, headH, pad, radius;
    private volatile List<Channel> rows = new ArrayList<>();
    private volatile GuideLineup lineup;
    private Listener listener;
    private int sel, top;
    private long windowStart, cursor;
    /** Now & Next instead of the half-hour grid. */
    private boolean nowNext;
    private final StringBuilder digits = new StringBuilder();
    private final Runnable applyDigits = this::jumpToTypedNumber;
    /** One for the main thread (the highlighted row, rows still waiting for a picture), one for the picture thread. */
    private final Painter live, pictures;

    GuideGridView(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        rowH = Ui.dp(31);
        gap = Ui.dp(4);
        chanW = Ui.dp(262);
        headH = Ui.dp(28);
        pad = Ui.dp(10);
        radius = Ui.dp(3);
        header.setTextSize(Ui.dp(15));
        header.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.setColor(0xFFFFFFFF);
        marker.setColor(0xCCF5D33C);
        live = new Painter();
        pictures = new Painter();
        long now = System.currentTimeMillis();
        windowStart = now - now % SLOT;
        cursor = now;
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setRows(List<Channel> channels, GuideLineup lineup, int select) {
        this.rows = channels;
        this.lineup = lineup;
        sel = Math.max(0, Math.min(select, channels.size() - 1));
        top = Math.max(0, sel - visibleRows() / 2);
        long now = System.currentTimeMillis();
        windowStart = now - now % SLOT;
        cursor = now;
        dataChanged(); // another section: none of the old pictures apply
        changed();
    }

    private int visibleRows() {
        int h = getHeight() > 0 ? getHeight() : Ui.dp(300);
        return Math.max(1, (int) ((h - headH) / (rowH + gap)));
    }

    private void changed() {
        clampTop();
        invalidate();
        removeCallbacks(drawAhead);
        postDelayed(drawAhead, 120); // once the remote is idle, draw the neighbours' pictures
        if (listener == null || rows.isEmpty()) return;
        Channel ch = rows.get(sel);
        listener.onSelect(ch, selectedProg(), lineup == null ? 0 : lineup.number(ch));
        int end = Math.min(rows.size(), top + visibleRows() + 2);
        listener.onNeedGuide(rows.subList(top, end));
    }

    private void clampTop() {
        int vis = visibleRows();
        if (sel < top) top = sel;
        if (sel >= top + vis) top = sel - vis + 1;
        top = Math.max(0, Math.min(top, Math.max(0, rows.size() - vis)));
    }

    /** The programme under the cursor on the selected row (null when the channel has no guide). */
    Guide.Prog selectedProg() {
        if (rows.isEmpty()) return null;
        for (Guide.Prog p : Guide.between(rows.get(sel), cursor, cursor + 1)) return p;
        return null;
    }

    int selectedIndex() {
        return sel;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        clampTop();
        dataChanged();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(drawAhead);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        float gridX = chanW + gap, gridW = w - gridX;
        long windowEnd = windowStart + WINDOW;
        long now = System.currentTimeMillis();
        Frame f = new Frame(w, windowStart, cursor, nowNext, now);

        // Time header: "Today" over the channels, then the three half-hour marks.
        c.drawText(L.date("EEE d MMM", windowStart).toString(), pad, headH - Ui.dp(9), header);
        if (nowNext) {
            c.drawText(L.t("Now"), gridX + Ui.dp(4), headH - Ui.dp(9), header);
            c.drawText(L.t("Next"), gridX + gridW / 2f + Ui.dp(4), headH - Ui.dp(9), header);
        } else for (int k = 0; k < 3; k++) {
            long t = windowStart + k * SLOT;
            c.drawText(time(t), gridX + gridW * k / 3f + Ui.dp(4), headH - Ui.dp(9), header);
        }
        int vis = visibleRows();
        for (int i = 0; i < vis && top + i < rows.size(); i++) {
            int idx = top + i;
            Channel ch = rows.get(idx);
            float y = headH + i * (rowH + gap);
            if (idx == sel) {
                live.row(c, ch, y, true, f, logoFor(ch)); // changes with every key press: drawn live
                continue;
            }
            Bitmap bm = rowCache.get(pictureKey(ch, f));
            if (bm != null) {
                c.drawBitmap(bm, 0, y, null);
            } else {
                // ◀ ▶ moved the time window: until this window's picture arrives, the row's picture from a window
                // nearby slides into place (the same programmes, moved by the time between), which costs one copy.
                if (!shifted(c, ch, y, f)) live.row(c, ch, y, false, f, logoFor(ch));
                want(ch, f, true);
            }
        }
        // Thin "now" marker when the current time is inside the window.
        if (now >= windowStart && now < windowEnd) {
            float x = gridX + gridW * (now - windowStart) / (float) WINDOW;
            c.drawRect(x - 1, headH - Ui.dp(4), x + 1, headH, marker);
        }
    }

    // ---- row pictures, drawn on a background thread ----

    private final Paint lines = new Paint();
    private final android.graphics.Rect src = new android.graphics.Rect();
    private final RectF dst = new RectF();

    /** Draws a row from its picture for a nearby time window, shifted to this one; false when there's none. */
    private boolean shifted(Canvas c, Channel ch, float y, Frame f) {
        if (f.nowNext) return false;
        Bitmap near = null;
        long at = 0;
        for (long d : new long[] {-SLOT, SLOT, -2 * SLOT, 2 * SLOT, -3 * SLOT, 3 * SLOT}) {
            near = rowCache.get(ch.key() + "@" + (f.windowStart + d));
            if (near != null) {
                at = f.windowStart + d;
                break;
            }
        }
        if (near == null || near.getWidth() != f.w) return false;
        int gridX = (int) (chanW + gap), bh = near.getHeight();
        float dx = (f.w - gridX) * (at - f.windowStart) / (float) WINDOW;
        src.set(0, 0, gridX, bh); // the channel cell doesn't move
        dst.set(0, y, gridX, y + bh);
        c.drawBitmap(near, src, dst, null);
        lines.setColor(LINES);
        c.drawRect(gridX, y, f.w, y + bh, lines);
        c.save();
        c.clipRect(gridX, y, f.w, y + bh);
        src.set(gridX, 0, f.w, bh);
        dst.set(gridX + dx, y, f.w + dx, y + bh);
        c.drawBitmap(near, src, dst, null);
        c.restore();
        return true;
    }

    /** What a row picture shows: the width, time window, cursor (for Now & Next), and what time it is. */
    private static final class Frame {
        final int w;
        final long windowStart, cursor, now;
        final boolean nowNext;

        Frame(int w, long windowStart, long cursor, boolean nowNext, long now) {
            this.w = w;
            this.windowStart = windowStart;
            this.cursor = cursor;
            this.nowNext = nowNext;
            this.now = now;
        }
    }

    private String pictureKey(Channel ch, Frame f) {
        return ch.key() + "@" + (f.nowNext ? "nn" : String.valueOf(f.windowStart));
    }

    /**
     * Unselected rows' pictures for the windows in use (~180 KB each), most recently used kept. 48: the screen, the
     * rows either side, and the next and previous windows. Evicted pictures are reused rather than reallocated.
     */
    private final java.util.LinkedHashMap<String, Bitmap> rowCache = new java.util.LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Bitmap> e) {
            if (size() <= 48) return false;
            synchronized (spare) {
                if (spare.size() < 6) spare.add(e.getValue());
            }
            return true;
        }
    };
    private final ArrayList<Bitmap> spare = new ArrayList<>();
    private boolean redrawPosted;
    private final java.util.HashSet<String> pending = new java.util.HashSet<>();
    /** Bumped when listings, favourites or the size change: pictures being drawn for the old data are dropped. */
    private volatile int generation;
    private static android.os.Handler painter;

    private static synchronized android.os.Handler painter() {
        if (painter == null) {
            android.os.HandlerThread t = new android.os.HandlerThread("guide-rows", android.os.Process.THREAD_PRIORITY_DISPLAY + 4);
            t.start();
            painter = new android.os.Handler(t.getLooper());
        }
        return painter;
    }

    /** Asks for a row's picture; {@code first} puts it ahead of rows drawn ahead. */
    private void want(final Channel ch, final Frame f, boolean first) {
        if (f.w <= 0) return;
        final String key = pictureKey(ch, f);
        if (rowCache.containsKey(key) || !pending.add(key)) return;
        final int gen = generation;
        final Bitmap logo = logoFor(ch);
        final int bh = (int) Math.ceil(rowH);
        Runnable job = () -> {
            if (gen != generation) return; // the data changed while this waited
            Bitmap bm = null;
            synchronized (spare) {
                while (!spare.isEmpty() && bm == null) {
                    Bitmap s = spare.remove(spare.size() - 1);
                    if (s.getWidth() == f.w && s.getHeight() == bh) bm = s;
                }
            }
            if (bm == null) bm = Bitmap.createBitmap(f.w, bh, Bitmap.Config.RGB_565); // opaque: half the size
            pictures.row(new Canvas(bm), ch, 0, false, f, logo);
            final Bitmap done = bm;
            post(() -> {
                pending.remove(key);
                if (gen != generation) return;
                rowCache.put(key, done);
                // Pictures arrive one after another: one redraw for the lot rather than one each.
                if (!redrawPosted) {
                    redrawPosted = true;
                    postOnAnimationDelayed(() -> {
                        redrawPosted = false;
                        invalidate();
                    }, 24);
                }
            });
        };
        if (first) painter().postAtFrontOfQueue(job);
        else painter().post(job);
    }

    /** Idle: draw the rows either side of the screen and the next and previous time windows. */
    private final Runnable drawAhead = () -> {
        if (rows.isEmpty() || getWidth() <= 0) return;
        long now = System.currentTimeMillis();
        int vis = visibleRows(), w = getWidth();
        Frame f = new Frame(w, windowStart, cursor, nowNext, now);
        int from = Math.max(0, top - 4), to = Math.min(rows.size(), top + vis + 4);
        for (int i = from; i < to; i++) if (i != sel) want(rows.get(i), f, false);
        if (nowNext) return;
        // ◀ ▶ moves the window by up to an hour and a half at a time (a programme's length): the next three windows.
        for (long other : new long[] {windowStart + SLOT, windowStart - SLOT, windowStart + 2 * SLOT, windowStart + 3 * SLOT}) {
            if (other + WINDOW < now - now % SLOT) continue; // the guide doesn't go back before now
            Frame g = new Frame(w, other, cursor, false, now);
            for (int i = top; i < Math.min(rows.size(), top + vis); i++) want(rows.get(i), g, false);
        }
    };

    /** Listings or favourites changed: redraw every row. */
    void dataChanged() {
        generation++;
        rowCache.clear();
        pending.clear();
        invalidate();
    }

    // ---- channel logos, right of the name ----

    private final java.util.HashMap<String, Bitmap> logos = new java.util.HashMap<>();
    private final java.util.HashSet<String> asked = new java.util.HashSet<>();

    /** The channel's logo, trimmed and scaled to the row, or null (then it's fetched, and the row redrawn). */
    private Bitmap logoFor(Channel ch) {
        if (ch.logo == null || ch.logo.isEmpty()) return null;
        Bitmap bm = logos.get(ch.key());
        if (bm != null || !asked.add(ch.key())) return bm;
        final String key = ch.key();
        final String url = ch.logo;
        Net.bitmap(getContext(), url, Ui.dp(96), loaded -> {
            if (loaded == null) return;
            // Empty border trimmed (logo files pad unevenly, which made them sit high), then scaled once to
            // the size it's drawn at, so each frame copies a small texture as-is. Done off the main thread:
            // it's per-pixel work, and a first scroll through a section meets a new logo on most rows.
            Net.IO.execute(() -> {
                Bitmap art = trimmed(url, loaded);
                float k = Math.min(LOGO_W / (float) art.getWidth(), LOGO_H / (float) art.getHeight());
                int w = Math.max(1, Math.round(art.getWidth() * k)), h = Math.max(1, Math.round(art.getHeight() * k));
                final Bitmap scaled = Bitmap.createScaledBitmap(art, w, h, true);
                post(() -> {
                    if (logos.size() > 120) { // a screenful at a time is all that's drawn
                        logos.clear();
                        asked.clear(); // (they'd never be asked for again otherwise)
                        asked.add(key);
                    }
                    logos.put(key, scaled);
                    // That row's pictures were drawn without its logo.
                    for (java.util.Iterator<String> it = rowCache.keySet().iterator(); it.hasNext(); ) {
                        if (it.next().startsWith(key + "@")) it.remove();
                    }
                    invalidate();
                });
            });
        });
        return null;
    }

    private final float LOGO_W = Ui.dp(50), LOGO_H = Ui.dp(19);

    /** Trimmed logos by address: the guide's info panel asked for the same few on every key press. */
    private static final android.util.LruCache<String, Bitmap> TRIMMED = new android.util.LruCache<>(12);

    /** Let go of trimmed logos (Home calls this when it's back on screen). */
    static void trimCache() {
        TRIMMED.evictAll();
    }

    static Bitmap trimmed(String url, Bitmap b) {
        String key = url == null ? null : url + "#" + b.getWidth(); // rows and the info panel load different sizes
        Bitmap t = key == null ? null : TRIMMED.get(key);
        if (t != null && !t.isRecycled()) return t;
        t = trim(b);
        if (key != null) TRIMMED.put(key, t);
        return t;
    }

    /**
     * The logo without its empty border. Scans in from each edge and stops at the first solid pixel, one row or
     * column at a time (reading every pixel of a 240 px logo was a fifth of the guide's main-thread time).
     */
    static Bitmap trim(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        if (w < 3 || h < 3) return b;
        int[] row = new int[Math.max(w, h)];
        int top = 0, bottom = h - 1, left = 0, right = w - 1;
        while (top < h && !solidRow(b, top, w, row)) top++;
        if (top >= h) return b; // nothing visible
        while (bottom > top && !solidRow(b, bottom, w, row)) bottom--;
        while (left < w && !solidCol(b, left, top, bottom, row)) left++;
        while (right > left && !solidCol(b, right, top, bottom, row)) right--;
        if (left == 0 && top == 0 && right == w - 1 && bottom == h - 1) return b;
        return Bitmap.createBitmap(b, left, top, right - left + 1, bottom - top + 1);
    }

    private static boolean solidRow(Bitmap b, int y, int w, int[] row) {
        b.getPixels(row, 0, w, 0, y, w, 1);
        for (int x = 0; x < w; x++) if ((row[x] >>> 24) > 24) return true;
        return false;
    }

    private static boolean solidCol(Bitmap b, int x, int top, int bottom, int[] col) {
        int n = bottom - top + 1;
        b.getPixels(col, 0, 1, x, top, 1, n);
        for (int i = 0; i < n; i++) if ((col[i] >>> 24) > 24) return true;
        return false;
    }

    /** For channels with no published schedule: "Live now · Comedy · OK to watch" instead of a bare "No information". */
    private static String liveLabel(Channel ch) {
        String g = ch.group == null ? "" : ch.group.trim();
        int sp = g.indexOf(' ');
        if (sp > 0) g = g.substring(0, sp); // first category only
        return g.isEmpty() || g.equalsIgnoreCase("general") ? "Live now  ·  OK to watch" : "Live now  ·  " + g + "  ·  OK to watch";
    }

    // ---- drawing one row ----

    /** Paints, a scratch rectangle and shortened titles: one set per thread that draws rows. */
    private final class Painter {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), bell = new Paint(Paint.ANTI_ALIAS_FLAG),
                bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        final android.text.TextPaint text = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG),
                bold = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        /** Shortened titles by text + width: shaping (Thai especially) is the costly part of a row. */
        final java.util.HashMap<String, CharSequence> fitted = new java.util.HashMap<>();
        final RectF r = new RectF();
        final Path arrow = new Path();

        Painter() {
            text.setTextSize(Ui.dp(15));
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            bold.setTextSize(Ui.dp(15));
            bold.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }

        /** One channel row at height y: channel cell (number, name, logo) and its programme blocks. */
        void row(Canvas c, Channel ch, float y, boolean selected, Frame f, Bitmap logo) {
            float w = f.w;
            float gridX = chanW + gap, gridW = w - gridX;
            long windowEnd = f.windowStart + WINDOW;
            fill.setColor(LINES);
            c.drawRect(0, y, w, y + rowH, fill);

            // Channel cell: "101  Thai PBS"
            r.set(0, y, chanW, y + rowH);
            cell(c, selected);
            text.setColor(selected ? HILITE_TEXT : CELL_TEXT);
            bold.setColor(selected ? HILITE_TEXT : CELL_TEXT);
            GuideLineup l = lineup;
            int num = l == null ? 0 : l.number(ch);
            String n = num > 0 ? String.valueOf(num) : "";
            float numW = bold.measureText("8888") + pad;
            c.drawText(n, pad, y + rowH - Ui.dp(9), bold);
            float logoW = 0;
            if (logo != null) {
                // Centred on the same line as the channel name: the cell's 2 dp light top edge puts the visual
                // middle of the row (and of the text) 1 dp below its geometric middle.
                float right = chanW - Ui.dp(8), cx = right - LOGO_W / 2, cy = y + rowH / 2 + Ui.dp(1);
                c.drawBitmap(logo, Math.round(cx - logo.getWidth() / 2f), Math.round(cy - logo.getHeight() / 2f), bitmapPaint);
                logoW = LOGO_W + Ui.dp(10);
            }
            clipped(c, ch.name, pad + numW, y + rowH - Ui.dp(9), chanW - pad * 2 - numW - logoW, text);

            if (f.nowNext) {
                nowNext(c, ch, y, selected, f);
                return;
            }
            // Programme blocks.
            List<Guide.Prog> progs = Guide.between(ch, f.windowStart, windowEnd);
            if (progs.isEmpty()) {
                r.set(gridX, y, w, y + rowH);
                cell(c, selected);
                text.setColor(selected ? HILITE_TEXT : DIM_TEXT);
                clipped(c, f.now >= f.windowStart && f.now < windowEnd ? L.t(liveLabel(ch)) : L.t("No listings"),
                        gridX + pad, y + rowH - Ui.dp(9), gridW - pad * 2, text);
                return;
            }
            long covered = f.windowStart;
            for (Guide.Prog p : progs) {
                if (p.start - covered > GAP) gapBlock(c, covered, p.start, y, selected, f);
                covered = Math.max(covered, p.stop);
                float x1 = gridX + gridW * Math.max(0, p.start - f.windowStart) / (float) WINDOW;
                float x2 = gridX + gridW * Math.min(WINDOW, p.stop - f.windowStart) / (float) WINDOW;
                if (x2 - x1 < Ui.dp(6)) continue;
                r.set(x1, y, x2 - gap, y + rowH);
                boolean hl = selected && p.start <= f.cursor && f.cursor < p.stop;
                cell(c, hl);
                float tx = x1 + pad;
                if (p.start < f.windowStart) { // started before the window: little ◀ like the classic guide
                    arrow(c, x1 + Ui.dp(4), y + rowH / 2, true, hl);
                    tx += Ui.dp(8);
                }
                boolean more = p.stop > windowEnd;
                if (more) arrow(c, x2 - gap - Ui.dp(4), y + rowH / 2, false, hl);
                text.setColor(hl ? HILITE_TEXT : CELL_TEXT);
                if (p.start > f.now && Reminders.has(getContext(), ch, p)) { // reminder set: a small bell before the title
                    bell(c, tx + Ui.dp(5), y + rowH / 2 + Ui.dp(1), hl);
                    tx += Ui.dp(16);
                }
                clipped(c, p.title, tx, y + rowH - Ui.dp(9), (x2 - gap) - tx - (more ? Ui.dp(12) : Ui.dp(6)), text);
            }
            if (windowEnd - covered > GAP) gapBlock(c, covered, windowEnd, y, selected, f);
        }

        void nowNext(Canvas c, Channel ch, float y, boolean selected, Frame f) {
            float w = f.w, gridX = chanW + gap, half = (w - gridX) / 2f;
            Guide.Prog[] nn = nowAndNext(ch, f.now);
            if (nn[0] == null && nn[1] == null) {
                r.set(gridX, y, w, y + rowH);
                cell(c, selected);
                text.setColor(selected ? HILITE_TEXT : DIM_TEXT);
                clipped(c, L.t(liveLabel(ch)), gridX + pad, y + rowH - Ui.dp(9), w - gridX - pad * 2, text);
                return;
            }
            for (int k = 0; k < 2; k++) {
                Guide.Prog p = nn[k];
                float x1 = gridX + k * half, x2 = x1 + half - (k == 0 ? gap : 0);
                r.set(x1, y, x2, y + rowH);
                boolean hl = selected && p != null && p.start <= f.cursor && f.cursor < p.stop;
                cell(c, hl);
                text.setColor(hl ? HILITE_TEXT : p == null ? DIM_TEXT : CELL_TEXT);
                String label = p == null ? (k == 0 ? L.t("No listing now") : "")
                        : (k == 0 ? L.f("until {t}", time(p.stop)) + "   " : time(p.start) + "   ") + p.title;
                float tx = x1 + pad;
                if (k == 1 && p != null && p.start > f.now && Reminders.has(getContext(), ch, p)) {
                    bell(c, tx + Ui.dp(5), y + rowH / 2 + Ui.dp(1), hl);
                    tx += Ui.dp(16);
                }
                clipped(c, label, tx, y + rowH - Ui.dp(9), x2 - tx - Ui.dp(6), text);
            }
        }

        /** A stretch the schedule doesn't cover: a quieter block reading "No listings". */
        void gapBlock(Canvas c, long from, long to, float y, boolean selectedRow, Frame f) {
            float gridX = chanW + gap, gridW = f.w - gridX;
            float x1 = gridX + gridW * Math.max(0, from - f.windowStart) / (float) WINDOW;
            float x2 = gridX + gridW * Math.min(WINDOW, to - f.windowStart) / (float) WINDOW;
            if (x2 - x1 < Ui.dp(6)) return;
            r.set(x1, y, to >= f.windowStart + WINDOW ? x2 : x2 - gap, y + rowH);
            boolean hl = selectedRow && from <= f.cursor && f.cursor < to;
            fill.setColor(hl ? HILITE : 0xB31C3A8E);
            c.drawRoundRect(r, radius, radius, fill);
            text.setColor(hl ? HILITE_TEXT : DIM_TEXT);
            clipped(c, L.t("No listings"), x1 + pad, y + rowH - Ui.dp(9), r.right - x1 - pad * 2, text);
        }

        void cell(Canvas c, boolean hl) {
            if (hl) {
                fill.setColor(HILITE);
                c.drawRoundRect(r, radius, radius, fill);
                return;
            }
            // Flat block with a lighter top edge: two plain draws, no per-cell gradient objects.
            fill.setColor(CELL_EDGE);
            c.drawRoundRect(r, radius, radius, fill);
            fill.setColor(CELL);
            c.drawRect(r.left, r.top + Ui.dp(2), r.right, r.bottom, fill);
        }

        /** A little bell: dome, rim and clapper. */
        void bell(Canvas c, float cx, float cy, boolean hl) {
            float s = Ui.dp(1);
            bell.setColor(hl ? HILITE_TEXT : HILITE);
            bell.setStyle(Paint.Style.FILL);
            RectF dome = new RectF(cx - 4.5f * s, cy - 6 * s, cx + 4.5f * s, cy + 3 * s);
            c.drawRoundRect(dome, 4.5f * s, 4.5f * s, bell);
            c.drawRect(cx - 6 * s, cy + 1.5f * s, cx + 6 * s, cy + 3.5f * s, bell);
            c.drawCircle(cx, cy + 5 * s, 1.6f * s, bell);
        }

        void arrow(Canvas c, float x, float cy, boolean left, boolean hl) {
            float s = Ui.dp(4);
            arrow.reset();
            if (left) {
                arrow.moveTo(x, cy);
                arrow.lineTo(x + s, cy - s);
                arrow.lineTo(x + s, cy + s);
            } else {
                arrow.moveTo(x, cy);
                arrow.lineTo(x - s, cy - s);
                arrow.lineTo(x - s, cy + s);
            }
            arrow.close();
            fill.setColor(hl ? HILITE_TEXT : 0xFFFFFFFF);
            c.drawPath(arrow, fill);
        }

        void clipped(Canvas c, String s, float x, float baseline, float maxW, android.text.TextPaint p) {
            if (maxW <= Ui.dp(8) || s == null) return;
            String k = (p == bold ? "b" : "t") + (int) maxW + "|" + s;
            CharSequence t = fitted.get(k);
            if (t == null) {
                if (fitted.size() > 600) fitted.clear();
                t = TextUtils.ellipsize(s, p, maxW, TextUtils.TruncateAt.END).toString();
                fitted.put(k, t);
            }
            c.drawText(t, 0, t.length(), x, baseline, p);
        }
    }

    private String time(long t) {
        return Ui.is24h(getContext())
                ? DateFormat.format("HH:mm", t).toString()
                : DateFormat.format("h.mma", t).toString().toLowerCase(); // "10.30pm", like the classic guide
    }

    // ---- remote ----

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (rows.isEmpty()) return super.onKeyDown(code, e);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (sel > 0) sel--;
                changed();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (sel < rows.size() - 1) sel++;
                changed();
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                sel = Math.max(0, sel - visibleRows());
                top = Math.max(0, top - visibleRows());
                changed();
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                sel = Math.min(rows.size() - 1, sel + visibleRows());
                top = top + visibleRows();
                changed();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                moveTime(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                moveTime(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (listener == null) return true;
                Guide.Prog at = selectedProg();
                if (at != null && at.start > System.currentTimeMillis() + 60_000L) listener.onRemind(rows.get(sel), at);
                else listener.onWatch(rows.get(sel), sel);
                return true;
            default:
                if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9 && digits.length() < 4) {
                    digits.append((char) ('0' + code - KeyEvent.KEYCODE_0));
                    removeCallbacks(applyDigits);
                    postDelayed(applyDigits, 2000);
                    if (listener != null) listener.onSelect(rows.get(sel), selectedProg(), -Integer.parseInt(digits.toString()));
                    return true;
                }
                return super.onKeyDown(code, e);
        }
    }

    // ---- Now & Next: each row shows what's on and what's next instead of the half-hour grid ----

    void setNowNext(boolean on) {
        if (nowNext == on) return;
        nowNext = on;
        long now = System.currentTimeMillis();
        cursor = now;
        windowStart = now - now % SLOT;
        dataChanged();
        changed();
    }

    boolean nowNext() {
        return nowNext;
    }

    /** The programme on now and the one after it (either may be null). */
    private static Guide.Prog[] nowAndNext(Channel ch, long now) {
        Guide.Prog cur = null, next = null;
        for (Guide.Prog p : Guide.between(ch, now, now + 12 * 60 * 60 * 1000L)) {
            if (cur == null && p.start <= now && now < p.stop) cur = p;
            else if (p.start >= now && next == null) next = p;
            if (cur != null && next != null && next.start >= cur.stop) break;
        }
        if (cur != null && next != null && next.start < cur.stop) next = null;
        return new Guide.Prog[] {cur, next};
    }

    /** Next/previous programme on the selected row; past the window edge the window shifts half an hour. */
    private void moveTime(boolean forward) {
        long now = System.currentTimeMillis();
        if (nowNext) { // ◀ ▶ step between Now and Next
            Guide.Prog[] nn = nowAndNext(rows.get(sel), now);
            cursor = forward && nn[1] != null ? nn[1].start : now;
            changed();
            return;
        }
        long earliest = now - now % SLOT;
        Channel ch = rows.get(sel);
        List<Guide.Prog> progs = Guide.between(ch, cursor - 26 * 60 * 60 * 1000L, cursor + 26 * 60 * 60 * 1000L);
        Guide.Prog cur = selectedProg();
        long target;
        if (forward) {
            target = cursor + SLOT;
            if (cur != null) target = cur.stop;
            else for (Guide.Prog p : progs) if (p.start > cursor) { target = p.start; break; }
        } else {
            target = cursor - SLOT;
            if (cur != null) {
                Guide.Prog prev = null;
                for (Guide.Prog p : progs) if (p.stop <= cur.start) prev = p;
                target = prev != null ? prev.start : cur.start - SLOT;
            }
            target = Math.max(target, Math.min(now, cursor));
            if (target < earliest) target = now;
        }
        cursor = Math.max(target, now - now % SLOT);
        if (cursor >= windowStart + WINDOW) windowStart = cursor - cursor % SLOT;
        while (cursor < windowStart && windowStart > earliest) windowStart -= SLOT;
        changed();
    }

    private void jumpToTypedNumber() {
        if (digits.length() == 0 || lineup == null) return;
        int wanted = Integer.parseInt(digits.toString());
        digits.setLength(0);
        for (int i = 0; i < rows.size(); i++) {
            if (lineup.number(rows.get(i)) == wanted) {
                sel = i;
                top = Math.max(0, i - visibleRows() / 2);
                break;
            }
        }
        changed();
    }
}
