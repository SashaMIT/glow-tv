package com.tv98.glow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
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
    private static final long SLOT = 30 * 60 * 1000L, WINDOW = 3 * SLOT, GAP = 5 * 60 * 1000L;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.text.TextPaint text = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final android.text.TextPaint bold = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG), header = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    /** Shortened titles by text + width: shaping (Thai especially) is the costly part of a frame. */
    private final java.util.HashMap<String, CharSequence> fitted = new java.util.HashMap<>();
    private final RectF r = new RectF();
    private final Path arrow = new Path();
    private final float rowH, gap, chanW, headH, pad, radius;
    private List<Channel> rows = new ArrayList<>();
    private GuideLineup lineup;
    private Listener listener;
    private int sel, top;
    private long windowStart, cursor;
    private final StringBuilder digits = new StringBuilder();
    private final Runnable applyDigits = this::jumpToTypedNumber;

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
        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        text.setTextSize(Ui.dp(15));
        text.setTypeface(medium);
        bold.setTextSize(Ui.dp(15));
        bold.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.setTextSize(Ui.dp(15));
        header.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.setColor(0xFFFFFFFF);
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
        changed();
    }

    private int visibleRows() {
        int h = getHeight() > 0 ? getHeight() : Ui.dp(300);
        return Math.max(1, (int) ((h - headH) / (rowH + gap)));
    }

    private void changed() {
        clampTop();
        invalidate();
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
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float gridX = chanW + gap, gridW = w - gridX;
        long windowEnd = windowStart + WINDOW;
        long now = System.currentTimeMillis();

        // Time header: "Today" over the channels, then the three half-hour marks.
        header.setColor(0xFFFFFFFF);
        c.drawText(DateFormat.format("EEE d MMM", windowStart).toString(), pad, headH - Ui.dp(9), header);
        if (nowNext) {
            c.drawText("Now", gridX + Ui.dp(4), headH - Ui.dp(9), header);
            c.drawText("Next", gridX + gridW / 2f + Ui.dp(4), headH - Ui.dp(9), header);
        } else for (int k = 0; k < 3; k++) {
            long t = windowStart + k * SLOT;
            c.drawText(time(t), gridX + gridW * k / 3f + Ui.dp(4), headH - Ui.dp(9), header);
        }
        // "Now" line across the grid.
        float y0 = headH;
        int vis = visibleRows();
        if (cachedWindow != windowStart || cachedWidth != getWidth()) {
            rowCache.clear();
            cachedWindow = windowStart;
            cachedWidth = getWidth();
        }
        // At most one new row picture per frame (3 was worse: p90 27-32 ms vs 21-26): after ◀ ▶ moves the time window every row is new, and making all
        // of them at once dropped frames; the rest are drawn directly until their turn comes.
        int budget = 1;
        boolean missing = false;
        for (int i = 0; i < vis && top + i < rows.size(); i++) {
            int idx = top + i;
            Channel ch = rows.get(idx);
            float y = y0 + i * (rowH + gap);
            if (idx == sel) {
                drawRow(c, ch, y, true, now); // the highlighted row changes with every key press: drawn live
            } else {
                // Other rows are drawn once into a picture and reused, so each costs one draw call per frame.
                android.graphics.Bitmap bm = rowCache.get(ch.key());
                if (bm == null && budget == 0) {
                    drawRow(c, ch, y, false, now);
                    missing = true;
                    continue;
                }
                if (bm == null) {
                    budget--;
                    int bw = (int) w, bh = (int) Math.ceil(rowH);
                    bm = null;
                    while (!spare.isEmpty() && bm == null) {
                        android.graphics.Bitmap s = spare.remove(spare.size() - 1);
                        if (s.getWidth() == bw && s.getHeight() == bh) {
                            s.eraseColor(0);
                            bm = s;
                        }
                    }
                    if (bm == null) bm = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888);
                    drawRow(new Canvas(bm), ch, 0, false, now);
                    rowCache.put(ch.key(), bm);
                }
                c.drawBitmap(bm, 0, y, null);
            }
        }
        if (missing) postInvalidateOnAnimation(); // next frame makes the next row's picture
        // Thin "now" marker when the current time is inside the window.
        if (now >= windowStart && now < windowEnd) {
            float x = gridX + gridW * (now - windowStart) / (float) WINDOW;
            fill.setShader(null);
            fill.setColor(0xCCF5D33C);
            c.drawRect(x - 1, headH - Ui.dp(4), x + 1, headH, fill);
        }
    }

    /** One channel row at height y: channel cell (number, name, logo) and its programme blocks. */
    private void drawRow(Canvas c, Channel ch, float y, boolean selected, long now) {
        float w = getWidth();
        float gridX = chanW + gap, gridW = w - gridX;
        long windowEnd = windowStart + WINDOW;

        // Channel cell: "101  Thai PBS"
        r.set(0, y, chanW, y + rowH);
        drawCell(c, selected);
        text.setColor(selected ? HILITE_TEXT : CELL_TEXT);
        bold.setColor(selected ? HILITE_TEXT : CELL_TEXT);
        int num = lineup == null ? 0 : lineup.number(ch);
        String n = num > 0 ? String.valueOf(num) : "";
        float numW = bold.measureText("8888") + pad;
        c.drawText(n, pad, y + rowH - Ui.dp(9), bold);
        float logoW = drawLogo(c, ch, y);
        drawClipped(c, ch.name, pad + numW, y + rowH - Ui.dp(9), chanW - pad * 2 - numW - logoW, text);

        if (nowNext) {
            drawNowNext(c, ch, y, selected, now);
            return;
        }
        // Programme blocks.
        List<Guide.Prog> progs = Guide.between(ch, windowStart, windowEnd);
        if (progs.isEmpty()) {
            r.set(gridX, y, w, y + rowH);
            drawCell(c, selected);
            text.setColor(selected ? HILITE_TEXT : DIM_TEXT);
            drawClipped(c, now >= windowStart && now < windowEnd ? liveLabel(ch) : "No listings",
                    gridX + pad, y + rowH - Ui.dp(9), gridW - pad * 2, text);
            return;
        }
        long covered = windowStart;
        for (Guide.Prog p : progs) {
            if (p.start - covered > GAP) drawGap(c, covered, p.start, y, selected);
            covered = Math.max(covered, p.stop);
            float x1 = gridX + gridW * Math.max(0, p.start - windowStart) / (float) WINDOW;
            float x2 = gridX + gridW * Math.min(WINDOW, p.stop - windowStart) / (float) WINDOW;
            if (x2 - x1 < Ui.dp(6)) continue;
            r.set(x1, y, x2 - gap, y + rowH);
            boolean hl = selected && p.start <= cursor && cursor < p.stop;
            drawCell(c, hl);
            float tx = x1 + pad;
            if (p.start < windowStart) { // started before the window: little ◀ like the classic guide
                drawArrow(c, x1 + Ui.dp(4), y + rowH / 2, true, hl);
                tx += Ui.dp(8);
            }
            boolean more = p.stop > windowEnd;
            if (more) drawArrow(c, x2 - gap - Ui.dp(4), y + rowH / 2, false, hl);
            text.setColor(hl ? HILITE_TEXT : CELL_TEXT);
            if (p.start > now && Reminders.has(getContext(), ch, p)) { // reminder set: a small bell before the title
                drawBell(c, tx + Ui.dp(5), y + rowH / 2 + Ui.dp(1), hl);
                tx += Ui.dp(16);
            }
            drawClipped(c, p.title, tx, y + rowH - Ui.dp(9), (x2 - gap) - tx - (more ? Ui.dp(12) : Ui.dp(6)), text);
        }
        if (windowEnd - covered > GAP) drawGap(c, covered, windowEnd, y, selected);
    }

    // ---- cached row pictures ----

    /**
     * Pictures of unselected rows for the current time window (~180 KB each). 36 kept (was 18): with nightly listings
     * most rows are full of programmes, so redrawing one on a miss costs a dropped frame, and a scroll down and back
     * passes ~27 rows. Evicted pictures are reused rather than reallocated.
     */
    private final java.util.LinkedHashMap<String, android.graphics.Bitmap> rowCache =
            new java.util.LinkedHashMap<String, android.graphics.Bitmap>(48, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, android.graphics.Bitmap> e) {
                    if (size() <= 36) return false;
                    if (spare.size() < 4) spare.add(e.getValue());
                    return true;
                }
            };
    private final java.util.ArrayList<android.graphics.Bitmap> spare = new java.util.ArrayList<>();
    private long cachedWindow = -1;
    private int cachedWidth = -1;

    /** Listings or favourites changed: redraw every row. */
    void dataChanged() {
        rowCache.clear();
        invalidate();
    }

    // ---- channel logos, right of the name ----

    private final java.util.HashMap<String, android.graphics.Bitmap> logos = new java.util.HashMap<>();
    private final java.util.HashSet<String> asked = new java.util.HashSet<>();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF logoBox = new RectF();

    /** Draws the channel's logo at the right of its cell, straight on the cell colour; returns the width used. */
    private float drawLogo(Canvas c, Channel ch, float y) {
        if (ch.logo == null || ch.logo.isEmpty()) return 0;
        android.graphics.Bitmap bm = logos.get(ch.key());
        if (bm == null) {
            if (asked.add(ch.key())) {
                final String key = ch.key();
                final String url = ch.logo;
                Net.bitmap(getContext(), url, Ui.dp(96), loaded -> {
                    if (loaded == null) return;
                    // Empty border trimmed (logo files pad unevenly, which made them sit high), then scaled once to
                    // the size it's drawn at, so each frame copies a small texture as-is. Done off the main thread:
                    // it's per-pixel work, and a first scroll through a section meets a new logo on most rows.
                    Net.IO.execute(() -> {
                        android.graphics.Bitmap art = trimmed(url, loaded);
                        float k = Math.min(LOGO_W / (float) art.getWidth(), LOGO_H / (float) art.getHeight());
                        int w = Math.max(1, Math.round(art.getWidth() * k)), h = Math.max(1, Math.round(art.getHeight() * k));
                        final android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(art, w, h, true);
                        post(() -> {
                            if (logos.size() > 120) { // a screenful at a time is all that's drawn
                                logos.clear();
                                asked.clear(); // (they'd never be asked for again otherwise)
                                asked.add(key);
                            }
                            logos.put(key, scaled);
                            rowCache.remove(key); // that row's picture was drawn without its logo
                            invalidate();
                        });
                    });
                });
            }
            return 0;
        }
        // Centred on the same line as the channel name: the cell's 2 dp light top edge puts the visual middle
        // of the row (and of the text) 1 dp below its geometric middle.
        float right = chanW - Ui.dp(8), cx = right - LOGO_W / 2, cy = y + rowH / 2 + Ui.dp(1);
        c.drawBitmap(bm, Math.round(cx - bm.getWidth() / 2f), Math.round(cy - bm.getHeight() / 2f), bitmapPaint);
        return LOGO_W + Ui.dp(10);
    }

    private final float LOGO_W = Ui.dp(50), LOGO_H = Ui.dp(19);

    /** The bitmap without its fully transparent border (the box around pixels with any real opacity). */
    /** Trimmed logos by address: the guide's info panel asked for the same few on every key press. */
    private static final android.util.LruCache<String, android.graphics.Bitmap> TRIMMED = new android.util.LruCache<>(12);

    /** Let go of trimmed logos (Home calls this when it's back on screen). */
    static void trimCache() {
        TRIMMED.evictAll();
    }

    static android.graphics.Bitmap trimmed(String url, android.graphics.Bitmap b) {
        String key = url == null ? null : url + "#" + b.getWidth(); // rows and the info panel load different sizes
        android.graphics.Bitmap t = key == null ? null : TRIMMED.get(key);
        if (t != null && !t.isRecycled()) return t;
        t = trim(b);
        if (key != null) TRIMMED.put(key, t);
        return t;
    }

    /**
     * The logo without its empty border. Scans in from each edge and stops at the first solid pixel, one row or
     * column at a time (reading every pixel of a 240 px logo was a fifth of the guide's main-thread time).
     */
    static android.graphics.Bitmap trim(android.graphics.Bitmap b) {
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
        return android.graphics.Bitmap.createBitmap(b, left, top, right - left + 1, bottom - top + 1);
    }

    private static boolean solidRow(android.graphics.Bitmap b, int y, int w, int[] row) {
        b.getPixels(row, 0, w, 0, y, w, 1);
        for (int x = 0; x < w; x++) if ((row[x] >>> 24) > 24) return true;
        return false;
    }

    private static boolean solidCol(android.graphics.Bitmap b, int x, int top, int bottom, int[] col) {
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

    /** A stretch the schedule doesn't cover: a quieter block reading "No information". */
    private void drawGap(Canvas c, long from, long to, float y, boolean selectedRow) {
        float gridX = chanW + gap, gridW = getWidth() - gridX;
        float x1 = gridX + gridW * Math.max(0, from - windowStart) / (float) WINDOW;
        float x2 = gridX + gridW * Math.min(WINDOW, to - windowStart) / (float) WINDOW;
        if (x2 - x1 < Ui.dp(6)) return;
        r.set(x1, y, to >= windowStart + WINDOW ? x2 : x2 - gap, y + rowH);
        boolean hl = selectedRow && from <= cursor && cursor < to;
        fill.setShader(null);
        fill.setColor(hl ? HILITE : 0xB31C3A8E);
        c.drawRoundRect(r, radius, radius, fill);
        text.setColor(hl ? HILITE_TEXT : DIM_TEXT);
        drawClipped(c, "No listings", x1 + pad, y + rowH - Ui.dp(9), r.right - x1 - pad * 2, text);
    }

    private void drawCell(Canvas c, boolean hl) {
        if (hl) {
            fill.setShader(null);
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

    private final Paint bell = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** A little bell: dome, rim and clapper. */
    private void drawBell(Canvas c, float cx, float cy, boolean hl) {
        float s = Ui.dp(1);
        bell.setColor(hl ? HILITE_TEXT : HILITE);
        bell.setStyle(Paint.Style.FILL);
        RectF dome = new RectF(cx - 4.5f * s, cy - 6 * s, cx + 4.5f * s, cy + 3 * s);
        c.drawRoundRect(dome, 4.5f * s, 4.5f * s, bell);
        c.drawRect(cx - 6 * s, cy + 1.5f * s, cx + 6 * s, cy + 3.5f * s, bell);
        c.drawCircle(cx, cy + 5 * s, 1.6f * s, bell);
    }

    private void drawArrow(Canvas c, float x, float cy, boolean left, boolean hl) {
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
        fill.setShader(null);
        fill.setColor(hl ? HILITE_TEXT : 0xFFFFFFFF);
        c.drawPath(arrow, fill);
    }

    private void drawClipped(Canvas c, String s, float x, float baseline, float maxW, android.text.TextPaint p) {
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

    /** Next/previous programme on the selected row; past the window edge the window shifts half an hour. */
    // ---- Now & Next: each row shows what's on and what's next instead of the half-hour grid ----

    private boolean nowNext;

    void setNowNext(boolean on) {
        if (nowNext == on) return;
        nowNext = on;
        long now = System.currentTimeMillis();
        cursor = now;
        windowStart = now - now % SLOT;
        rowCache.clear();
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

    private void drawNowNext(Canvas c, Channel ch, float y, boolean selected, long now) {
        float w = getWidth(), gridX = chanW + gap, half = (w - gridX) / 2f;
        Guide.Prog[] nn = nowAndNext(ch, now);
        if (nn[0] == null && nn[1] == null) {
            r.set(gridX, y, w, y + rowH);
            drawCell(c, selected);
            text.setColor(selected ? HILITE_TEXT : DIM_TEXT);
            drawClipped(c, liveLabel(ch), gridX + pad, y + rowH - Ui.dp(9), w - gridX - pad * 2, text);
            return;
        }
        for (int k = 0; k < 2; k++) {
            Guide.Prog p = nn[k];
            float x1 = gridX + k * half, x2 = x1 + half - (k == 0 ? gap : 0);
            r.set(x1, y, x2, y + rowH);
            boolean hl = selected && p != null && p.start <= cursor && cursor < p.stop;
            drawCell(c, hl);
            text.setColor(hl ? HILITE_TEXT : p == null ? DIM_TEXT : CELL_TEXT);
            String label = p == null ? (k == 0 ? "No listing now" : "")
                    : (k == 0 ? "until " + time(p.stop) + "   " : time(p.start) + "   ") + p.title;
            float tx = x1 + pad;
            if (k == 1 && p != null && p.start > now && Reminders.has(getContext(), ch, p)) {
                drawBell(c, tx + Ui.dp(5), y + rowH / 2 + Ui.dp(1), hl);
                tx += Ui.dp(16);
            }
            drawClipped(c, label, tx, y + rowH - Ui.dp(9), x2 - tx - Ui.dp(6), text);
        }
    }

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
